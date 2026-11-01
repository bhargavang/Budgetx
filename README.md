# BudgetX — AI-Powered Personal Finance Manager

A microservices application that helps users manage their budget, track expenses, and receive
AI-driven investment recommendations based on their financial profile and market trends.

```
Client (React / Flutter)
        │
        ▼
┌──────────────────┐
│   API Gateway    │ :8080   JWT validation, rate limiting, CORS, Swagger aggregation
└────────┬─────────┘
         │  (lb:// via Eureka :8761, config from Config Server :8888)
   ┌─────┼──────┬─────────┬─────────┬──────────┬──────────┬────────────┐
   ▼     ▼      ▼         ▼         ▼          ▼          ▼            ▼
 User   EMI  Expense   Budget   AI-Agent   Market   Investment  Notification
 :8081 :8082  :8083    :8084     :8085     :8086      :8087        :8088
```

Each service owns its MySQL database. Inter-service calls use OpenFeign (sync) and Kafka (async).

## Status

| Module | Status |
|---|---|
| eureka-server | Done |
| config-server | Done (native backend, `git` profile supported) |
| api-gateway | Done (routes for all services, JWT filter, Redis rate limiting, CORS) |
| user-service | Done (register, login, refresh, profile, internal Feign endpoints) |
| emi-service | Done (loan CRUD, EMI calculation, outstanding balance, monthly total) |
| expense-service | Done (seeded categories, CRUD, monthly/yearly summaries, Kafka `budget.expense.logged`) |
| frontend | Done (React web app / installable PWA for phone and laptop: login, dashboard, expenses, EMIs, profile) |
| budget, ai-agent, market-data, investment, notification | Next phases |

Gateway routes for the later services are already defined, so each one works through the
gateway as soon as it registers with Eureka.

## Prerequisites

JDK 17+, Maven 3.9+, Docker with Compose v2. Spring Boot 3.3.0, Spring Cloud 2023.0.2.

## Configuration

Secrets are never committed. Copy the template and fill it in:

```bash
cp .env.example .env
# JWT_SECRET must be at least 32 characters:
openssl rand -base64 48
```

| Variable | Description | Example |
|---|---|---|
| MYSQL_HOST | MySQL hostname | localhost |
| MYSQL_PORT | MySQL port | 3306 |
| MYSQL_USERNAME | DB username | budgetx |
| MYSQL_PASSWORD | DB password | (your choice) |
| MYSQL_ROOT_PASSWORD | MySQL root password (Docker only) | (your choice) |
| REDIS_HOST | Redis hostname | localhost |
| KAFKA_BOOTSTRAP_SERVERS | Kafka servers | localhost:9092 |
| JWT_SECRET | JWT signing secret, shared by gateway and user-service | 48 random bytes, base64 |
| OPENAI_API_KEY | OpenAI API key (ai-agent-service) | sk-... |
| ALPHAVANTAGE_API_KEY | Market data API key (market-data-service) | your-key |
| CORS_ALLOWED_ORIGINS | Origins allowed by the gateway | http://localhost:3000 |
| EUREKA_URL | Eureka URL | http://localhost:8761/eureka/ |
| CONFIG_SERVER_URL | Config server URL | http://localhost:8888 |

## Running

### Everything in Docker

```bash
mvn clean package -DskipTests
docker compose --profile all up -d --build
```

The web app is at http://localhost:3000 (set `FRONTEND_PORT=80` on a server). Its nginx container
proxies `/api` to the gateway, so the browser talks to one origin. The gateway (8080) and Eureka
dashboard (8761) are also published; business services are reached through the gateway.

### Local development

```bash
# 1. Infrastructure (MySQL, Redis, Zookeeper, Kafka)
docker compose up -d

# 2. Platform services, each in its own terminal, in this order
export JWT_SECRET=... MYSQL_PASSWORD=...
cd eureka-server && mvn spring-boot:run
cd config-server && mvn spring-boot:run
cd api-gateway   && mvn spring-boot:run

# 3. Business services, any order
cd user-service  && mvn spring-boot:run

# 4. Web app on http://localhost:3000 (Vite proxies /api to localhost:8080)
cd frontend && npm install && npm run dev
```

## Frontend

`frontend/` is a React 18 + Vite app. It is responsive (bottom tab bar on phones, sidebar on
laptops) and installable as a PWA: open it in Chrome or Safari on a phone and choose
*Add to Home Screen*. The service worker caches only the app shell, never `/api` responses.
Screens for budget, AI recommendations, portfolio and notifications show as *Coming soon*
until those services are built. Note that browsers only offer installation over HTTPS
(or on localhost).

`infrastructure/mysql/init.sql` creates all eight databases and grants the `budgetx` account
access on the first MySQL start. Tables are created by JPA (`ddl-auto: update`).

## Security model

- **user-service** issues HS512 JWTs: a 15-minute access token and a 7-day refresh token,
  distinguished by a `type` claim so a refresh token can't be used as an access token.
- **api-gateway** validates the access token on every request except `/api/auth/**` and docs,
  strips any client-supplied `X-User-Id` / `X-User-Email`, and forwards the verified identity in
  those headers. Downstream services can trust them.
- Internal Feign endpoints (`/api/users/{id}/salary`, `/api/users/{id}/profile`,
  `/api/emis/total`, `/api/expenses/total`) return **403** at the gateway.
- Requests are rate limited per user (or per IP when anonymous) with Redis: 20/s, burst 40.
- Passwords are hashed with BCrypt. DTOs are validated with Jakarta Validation.

## API (user-service)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | /api/auth/register | none | Create an account; returns tokens |
| POST | /api/auth/login | none | Returns access + refresh tokens |
| POST | /api/auth/refresh | none | Exchange a refresh token for new tokens |
| GET | /api/users/me | Bearer | Current user |
| PATCH | /api/users/me | Bearer | Partial update (salary, risk profile, ...) |
| PUT | /api/users/me/password | Bearer | Change password |
| DELETE | /api/users/me | Bearer | Delete account |
| GET | /api/users/{id}/salary | internal | Monthly salary (budget-service) |
| GET | /api/users/{id}/profile | internal | Age, salary, risk profile (ai-agent-service) |

## API (emi-service)

Business services read the caller from the `X-User-Id` header that the gateway sets.

| Method | Path | Description |
|---|---|---|
| POST | /api/emis | Add a loan. `emiAmount` is optional and computed from principal, rate and tenure if omitted |
| GET | /api/emis?status=ACTIVE | List loans with instalments paid/remaining and outstanding principal |
| GET | /api/emis/summary | Active loan count, this month's EMI total, total outstanding |
| GET / PUT / DELETE | /api/emis/{id} | Read, replace, delete one loan |
| PATCH | /api/emis/{id}/close | Mark a loan as closed (foreclosed or fully paid) |
| GET | /api/emis/total?userId=&month=&year= | internal: EMI due in a month (budget-service) |

## API (expense-service)

| Method | Path | Description |
|---|---|---|
| GET | /api/expense-categories | The 15 seeded categories, each flagged essential or discretionary |
| POST | /api/expenses | Log an expense; publishes `budget.expense.logged` to Kafka after commit |
| GET | /api/expenses?from=&to=&categoryId=&page=&size= | Paged list, defaults to the current month |
| GET | /api/expenses/summary/month?month=&year= | Total, essential vs discretionary, and per-category breakdown |
| GET | /api/expenses/summary/year?year= | Monthly totals for a year |
| GET / PUT / DELETE | /api/expenses/{id} | Read, replace, delete one expense |
| GET | /api/expenses/total?userId=&month=&year= | internal: total spent in a month (budget-service) |

Kafka event on `budget.expense.logged` (key = userId):

```json
{"userId":1,"expenseId":1,"amount":3200,"category":"Groceries","expenseDate":"2026-10-20"}
```

Quick test through the gateway:

```bash
curl -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"fullName":"Asha Rao","email":"asha@example.com","password":"s3cure-pass","monthlySalary":85000}'

TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"asha@example.com","password":"s3cure-pass"}' | jq -r .accessToken)

curl localhost:8080/api/users/me -H "Authorization: Bearer $TOKEN"
```

Swagger UI: http://localhost:8081/swagger-ui.html per service, or
http://localhost:8080/swagger-ui.html at the gateway with a dropdown for every service.

## Tests

```bash
mvn test
```

33 tests across the modules: JWT and auth-flow tests in user-service, JWT filter tests in
api-gateway, EMI maths and API tests in emi-service, and API, summary and Kafka-publish tests in
expense-service. Integration tests run on H2, so no Docker is needed for `mvn test`.

## Disclaimer

BudgetX provides AI-generated financial guidance for educational purposes. It is NOT a registered
financial advisor. Always consult a SEBI-registered advisor before making investment decisions.
Past performance does not guarantee future results.

## License

MIT
