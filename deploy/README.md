# Deploy BudgetX on Oracle Cloud (Always Free)

## 1. Create the VM (about 5 minutes)
1. Sign in at https://cloud.oracle.com and open **Compute > Instances > Create instance**.
2. **Image:** Canonical Ubuntu 22.04 or 24.04.
3. **Shape:** Change shape > Ampere > `VM.Standard.A1.Flex`, 4 OCPUs and 24 GB RAM (all within Always Free).
   If Oracle says "out of capacity", try 2 OCPU / 12 GB or another availability domain.
4. **SSH keys:** "Generate a key pair for me" and download the private key (or paste your own public key).
5. Create, wait until it is *Running*, and copy the **Public IP address**.

## 2. Open port 8080 in Oracle's network
Instance page > Subnet link > Security Lists > Default Security List > **Add Ingress Rules**:
Source CIDR `0.0.0.0/0`, IP Protocol `TCP`, Destination Port Range `8080`.

## 3. Run the deploy script on the VM
```bash
ssh -i <path-to-private-key> ubuntu@<PUBLIC_IP>

git clone https://github.com/bhargavang/budgetx.git   # private repo: username + a read-only GitHub token as the password
bash budgetx/deploy/oracle-setup.sh
```
The first run takes about 10-15 minutes (Docker install, Maven downloads, image builds).
It writes `budgetx/.env` with random secrets, starts everything, and opens port 8080 in the VM firewall.

For a read-only token: GitHub > Settings > Developer settings > Fine-grained tokens >
Generate new token > Repository access: only `budgetx` > Permissions: Contents = Read-only.
Type it on the VM only; never paste it into a chat.

## 4. Check it
- `http://<PUBLIC_IP>:8080/` opens the web app (create an account to start)
- `http://<PUBLIC_IP>:8080/actuator/health` should return `{"status":"UP"}`
- `http://<PUBLIC_IP>:8080/swagger-ui.html` lists the APIs

## Turn on AI guidance (optional)
Guidance works without any key, using BudgetX's built-in rules. To add AI-personalised explanations
(checked against the same rules), create an API key at https://console.anthropic.com, then on the VM:
```bash
cd ~/budgetx && nano .env        # set ANTHROPIC_API_KEY=... (type it here only, never in a chat)
bash deploy/oracle-setup.sh      # restarts with the key
```
Each user gets at most `AI_DAILY_LIMIT_PER_USER` (default 5) AI calls a day, which keeps the bill
bounded. `ANTHROPIC_MODEL=claude-haiku-5-5` in `.env` is much cheaper than the default.

## Updating later
```bash
cd ~/budgetx && bash deploy/oracle-setup.sh   # pulls, rebuilds, restarts; keeps .env
```

## Useful commands
```bash
cd ~/budgetx
C="sudo docker compose -f docker-compose.yml -f deploy/docker-compose.prod.yml --profile all"
$C ps                       # status
$C logs -f api-gateway      # logs of one service
$C restart user-service     # restart one service
$C down                     # stop (data in the mysql volume is kept)
```

Only port 8080 (the API gateway) is reachable from the internet. MySQL, Redis, Kafka and Eureka
listen on 127.0.0.1 only. To look at the Eureka dashboard, tunnel it:
`ssh -i <key> -L 8761:localhost:8761 ubuntu@<PUBLIC_IP>` then open http://localhost:8761.
