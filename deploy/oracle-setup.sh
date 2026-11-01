#!/usr/bin/env bash
# One-command deploy of BudgetX on an Oracle Cloud Always Free VM (Ubuntu 22.04/24.04, ARM or x86).
#
#   git clone https://github.com/bhargavang/budgetx.git && bash budgetx/deploy/oracle-setup.sh
#
# Safe to re-run: it pulls the latest code, rebuilds and restarts. An existing .env is kept.
# What it does: installs Docker, adds swap on small VMs, writes .env with random secrets,
# builds the jars inside a Maven container, starts the stack, and opens ports 8080 and 3000 in the
# VM firewall. Only the API gateway (8080) is published; MySQL, Redis, Kafka and Eureka
# are bound to 127.0.0.1 (see deploy/docker-compose.prod.yml).
set -euo pipefail

REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_DIR"
log() { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }

if [ "$(id -u)" -eq 0 ]; then SUDO=""; else SUDO="sudo"; fi

# ---------- 1. Docker ----------
if ! command -v docker >/dev/null 2>&1; then
  log "Installing Docker"
  curl -fsSL https://get.docker.com | $SUDO sh
  $SUDO usermod -aG docker "${SUDO_USER:-$USER}" || true
fi
$SUDO systemctl enable --now docker >/dev/null 2>&1 || true
DOCKER="$SUDO docker"

# ---------- 2. Swap (only if RAM < 8 GB and no swap yet) ----------
MEM_KB=$(awk '/MemTotal/ {print $2}' /proc/meminfo)
if [ "$MEM_KB" -lt 8000000 ] && ! swapon --show | grep -q .; then
  log "Adding 4 GB swap (VM has $((MEM_KB / 1024)) MB RAM)"
  $SUDO fallocate -l 4G /swapfile
  $SUDO chmod 600 /swapfile
  $SUDO mkswap /swapfile >/dev/null
  $SUDO swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | $SUDO tee -a /etc/fstab >/dev/null
fi

# ---------- 3. Latest code ----------
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  log "Pulling latest code"
  git pull --ff-only || echo "git pull skipped (local changes or no upstream); using the current checkout"
fi

# ---------- 4. .env with random secrets ----------
PUBLIC_IP=$(curl -fsS --max-time 5 https://ifconfig.me 2>/dev/null || curl -fsS --max-time 5 https://api.ipify.org 2>/dev/null || echo "")
if [ ! -f .env ]; then
  log "Writing .env with random secrets"
  rand() { openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c "$1"; }
  umask 077
  cat > .env <<ENVEOF
MYSQL_ROOT_PASSWORD=$(rand 32)
MYSQL_USERNAME=budgetx
MYSQL_PASSWORD=$(rand 32)
JWT_SECRET=$(rand 64)
OPENAI_API_KEY=
ALPHAVANTAGE_API_KEY=
CORS_ALLOWED_ORIGINS=http://localhost:3000${PUBLIC_IP:+,http://$PUBLIC_IP:8080,http://$PUBLIC_IP:3000}
ENVEOF
  umask 022
else
  log "Keeping existing .env"
fi

# ---------- 5. Build jars (Maven in Docker, works on ARM and x86) ----------
log "Building jars with Maven (first run downloads dependencies, takes a few minutes)"
$DOCKER volume create budgetx-m2 >/dev/null
$DOCKER run --rm \
  -v "$REPO_DIR":/build -w /build \
  -v budgetx-m2:/root/.m2 \
  maven:3.9-eclipse-temurin-17 \
  mvn -q -B clean package -DskipTests
# Jars built as root inside the container; hand them back to the login user.
$SUDO chown -R "$(id -u):$(id -g)" "$REPO_DIR" 2>/dev/null || true

# ---------- 6. Start the stack ----------
log "Building images and starting BudgetX"
COMPOSE="$DOCKER compose -f docker-compose.yml -f deploy/docker-compose.prod.yml --profile all"
$COMPOSE up -d --build --remove-orphans

# ---------- 7. Open ports 8080 (gateway + built-in web app) and 3000 (React app) ----------
# Oracle's Ubuntu images ship iptables rules that REJECT everything except SSH.
FRONTEND_PORT=$(grep -E '^FRONTEND_PORT=' .env 2>/dev/null | cut -d= -f2); FRONTEND_PORT=${FRONTEND_PORT:-3000}
log "Opening ports 8080 and $FRONTEND_PORT in the VM firewall"
for PORT in 8080 "$FRONTEND_PORT"; do
  if command -v ufw >/dev/null 2>&1 && $SUDO ufw status | grep -q "Status: active"; then
    $SUDO ufw allow "$PORT"/tcp
  fi
  if ! $SUDO iptables -C INPUT -p tcp --dport "$PORT" -m state --state NEW -j ACCEPT 2>/dev/null; then
    $SUDO iptables -I INPUT 6 -p tcp --dport "$PORT" -m state --state NEW -j ACCEPT 2>/dev/null \
      || $SUDO iptables -I INPUT -p tcp --dport "$PORT" -m state --state NEW -j ACCEPT
  fi
done
if command -v netfilter-persistent >/dev/null 2>&1; then
  $SUDO netfilter-persistent save >/dev/null 2>&1 || true
elif [ -d /etc/iptables ]; then
  $SUDO sh -c 'iptables-save > /etc/iptables/rules.v4'
fi

# ---------- 8. Wait for the gateway and service discovery ----------
# The gateway reports UP before it has learned the services from Eureka; until then routes
# answer 503. Wait for a real answer from user-service (401 for a bad login is fine).
log "Waiting for the services to come up (Spring Boot + Eureka need 2-4 minutes)"
READY=no
for i in $(seq 1 90); do
  CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST http://localhost:8080/api/auth/login \
    -H 'Content-Type: application/json' -d '{"email":"probe@example.com","password":"probe-pass"}' || true)
  case "$CODE" in 200|400|401) READY=yes; break ;; esac
  sleep 5
done
$COMPOSE ps

echo
if [ "$READY" = yes ]; then
  log "BudgetX is up. Running the smoke test"
  bash "$REPO_DIR/deploy/smoke-test.sh" http://localhost:8080 || true
else
  log "Services not ready yet. Check logs with: sudo docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml logs -f api-gateway user-service"
fi
cat <<MSG

  Web app:  http://${PUBLIC_IP:-<your-public-ip>}:${FRONTEND_PORT}/   (React app)
            http://${PUBLIC_IP:-<your-public-ip>}:8080/   (built-in app)
  Gateway:  http://${PUBLIC_IP:-<your-public-ip>}:8080/actuator/health
  Swagger:  http://${PUBLIC_IP:-<your-public-ip>}:8080/swagger-ui.html

  If the URL above times out from your laptop, add an ingress rule in Oracle Cloud:
  Networking > Virtual Cloud Networks > (your VCN) > Security Lists > Default Security List
  > Add Ingress Rule: Source CIDR 0.0.0.0/0, IP Protocol TCP, Destination Port 8080, and another for port ${FRONTEND_PORT}.

MSG
