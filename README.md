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
| budget-service | Done (salary - EMIs - spending via Feign, investable amount, Kafka `budget.overspend`) |
| ai-agent-service | Done (free educational guidance: safety rules first, optional Claude AI checked against them) |
| market-data, investment, notification | Next phases |

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
| ANTHROPIC_API_KEY | Optional Claude API key; empty = rules-only guidance | sk-ant-... |
| ANTHROPIC_MODEL | Claude model for guidance | claude-opus-5-5 |
| AI_DAILY_LIMIT_PER_USER | AI calls per user per day, then rules-only | 5 |
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

Only the gateway (8080) and Eureka dashboard (8761) are published; business services are reached
through the gateway.

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
```

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

## API (budget-service)

| Method | Path | Description |
|---|---|---|
| GET | /api/budgets/current | This month: salary, EMIs, spending, disposable and investable amounts |
| GET | /api/budgets/summary?userId=&month=&year= | internal: same, for ai-agent-service |

Spending uses the higher of this month so far and last month, so the investable amount isn't
inflated early in the month. Investable = 50/60/70% of what's left (conservative/moderate/aggressive).
When an expense pushes a month over the salary, `budget.overspend` is published once for that month.

## AI guidance (ai-agent-service)

Free, educational guidance that users can trust. It is not personalised investment advice (that needs
SEBI RIA registration), and every screen says so.

| Method | Path | Description |
|---|---|---|
| GET / PUT | /api/ai/safety-profile | Emergency savings, dependents, term and health insurance (user-reported) |
| POST | /api/recommendations/generate | Build guidance now |
| GET | /api/recommendations/latest | Last guidance (every one is stored for review) |
| GET | /api/ai/status | Whether AI is on and calls left today |

How it stays safe:

1. **Rules first, AI can't override them.** Overspending blocks investing. Heavy EMIs are flagged. An
   emergency fund (6 months of outgo) takes 70% of savings while under 3 months covered, 40% up to 6.
   Health cover, and pure term cover when someone depends on you, come before investing.
2. **Caps.** Equity at most 30/50/70% by risk profile and never above 100 minus age. Gold at most 15%.
   PPF limited to ₹12,500/month.
3. **Categories only.** Index fund SIP, debt fund, PPF, FD/RD, gold ETF. Titles and descriptions come from
   our code; the AI only picks percentages and writes short reasons.
4. **AI output is untrusted.** Claude (official Anthropic Java SDK, structured JSON output) gets anonymous
   numbers only. Its answer is validated: any reason naming a brand, a banned instrument (crypto, F&O,
   penny stocks, ULIPs, chit funds...), a link, or a return figure is removed, and if the plan then breaks
   100%, the equity cap or the gold cap, the rules-only split is shown instead, with a note saying so.
5. **No key, no problem.** Without `ANTHROPIC_API_KEY` everything works rules-only. AI calls are capped per
   user per day to keep a free service affordable.

## Tests

```bash
mvn test
```

85 tests across the modules: JWT and auth-flow tests in user-service, JWT filter tests in
api-gateway, EMI maths and API tests in emi-service, and API, summary and Kafka-publish tests in
expense-service, budget maths, API and overspend tests in budget-service, and rule-engine, AI-validator and guidance API tests in ai-agent-service. Integration tests run on H2, so no Docker is needed for `mvn test`.

## Disclaimer

BudgetX provides AI-generated financial guidance for educational purposes. It is NOT a registered
financial advisor. Always consult a SEBI-registered advisor before making investment decisions.
Past performance does not guarantee future results.

## License

MIT
