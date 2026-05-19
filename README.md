# Nexus Bank

Academic project for **Advanced Web Technologies (2025/2026)**.  
A digital retail banking platform built on a Spring Boot microservices architecture, modelled after modern challenger banks (Revolut, N26, Monzo).

**Team:** Anes Ćenanović · Faris Crnčalo · Daris Mujkić · Amina Kazazović

---

## Architecture

```
Browser (Angular)
      │
      ▼
 API Gateway :8080        ← JWT validation, routing, CORS
      │
      ├── user-service        :8081  ← auth, identity, RBAC
      ├── account-service     :8082  ← accounts, debit cards
      ├── transaction-service :8083  ← transfers, FX, statements
      └── loan-service        :8084  ← applications, approval, repayment

 Discovery Server (Eureka) :8761
 RabbitMQ                  :5672
```

Each service has its own MySQL database. Services never share a database.

| Service | Port | DB Port |
|---|---|---|
| user-service | 8081 | 3310 |
| account-service | 8082 | 3307 |
| transaction-service | 8083 | 3308 |
| loan-service | 8084 | 3309 |

---

## Tech Stack

- **Backend:** Java 17, Spring Boot 3.5, Spring Cloud 2025.0 (Gateway, Eureka, LoadBalancer)
- **Security:** Spring Security, JJWT 0.12 (RS256)
- **Messaging:** RabbitMQ (loan disbursement events)
- **Persistence:** Spring Data JPA, MySQL 8
- **API Docs:** Springdoc OpenAPI / Swagger UI
- **Frontend:** Angular (port 4200)
- **Tests:** JUnit 5, Mockito, Testcontainers (MySQL), Spring Security Test
- **Coverage:** JaCoCo 0.8.12

---

## Running the Project

### Prerequisites
- Java 17+, Maven 3.9+, Docker Desktop

### Start infrastructure

Each service ships with a `compose.yaml`. Start the databases and RabbitMQ first:

```bash
# From the repo root
docker compose -f compose-rabbitmq.yaml up -d
docker compose -f user-service/compose.yaml up -d
docker compose -f account-service/compose.yaml up -d
docker compose -f transaction-service/compose.yaml up -d
docker compose -f loan-service/compose.yaml up -d
```

### Start services (in order)

```bash
cd discovery-server  && ./mvnw spring-boot:run &
cd user-service      && ./mvnw spring-boot:run &
cd account-service   && ./mvnw spring-boot:run &
cd transaction-service && ./mvnw spring-boot:run &
cd loan-service      && ./mvnw spring-boot:run &
cd api-gateway       && ./mvnw spring-boot:run &
```

### Start frontend

```bash
cd frontend && npm install && ng serve
```

Open `http://localhost:4200`.

---

## Authentication

All requests to protected endpoints must carry a `Bearer` token in the `Authorization` header. The API gateway validates the signature and forwards the caller's identity to downstream services via `X-User-Id`, `X-User-Email`, and `X-User-Role` headers.

### Login

```http
POST /api/auth/login
Content-Type: application/json

{ "email": "admin@nexusbank.com", "password": "admin123" }
```

Response includes `token`, `userId`, `email`, `role`, and `expiresIn` (ms).

### Logout / Token Invalidation

```http
POST /api/auth/logout
Authorization: Bearer <token>
```

Returns `204 No Content`. The token's JTI is written to the `revoked_tokens` table in user-service. Both user-service and the API gateway check this list on every authenticated request — a revoked token is rejected with `401` even if its signature is still cryptographically valid and it has not yet expired.

#### How it works

1. `JwtUtil.generateToken()` embeds a UUID `jti` claim in every issued token.
2. `POST /api/auth/logout` calls `TokenRevocationService.revokeToken()`, which persists the JTI.
3. `JwtAuthFilter` (user-service) checks `TokenRevocationService.isRevoked(jti)` before granting access.
4. `JwtAuthenticationFilter` (api-gateway) calls `GET /api/internal/auth/revoked/{jti}` on user-service via a reactive `WebClient` before forwarding requests to any downstream service. The check fails open — if user-service is unreachable the token is treated as valid to prevent cascading outages.

### Seeded test users

| Email | Password | Role |
|---|---|---|
| admin@nexusbank.com | admin123 | ADMIN |
| ana.kovacevic@nexusbank.com | password2 | TELLER |
| marko.nikolic@nexusbank.com | password1 | CUSTOMER |

### JWT key pair

Tokens are signed with an RSA private key held **only** by user-service. The API gateway and downstream services verify tokens using the corresponding public key and cannot forge new tokens.

Keys are stored under each service's `src/main/resources/keys/` directory (excluded from version control).

---

## Roles and Access

| Role | Permissions |
|---|---|
| CUSTOMER | Own profile, own accounts/cards, transfers, loan applications |
| TELLER | Register customers, open accounts, deposit/withdraw, view any account |
| LOAN_OFFICER | Review loan queue, approve/reject applications |
| ADMIN | All of the above + close accounts, system stats dashboard |

---

## Functional Requirements

| ID | Description | Service |
|---|---|---|
| F01 | Tellers/admins register clients (KYC) | user-service |
| F02 | Login → signed JWT with configurable expiry and logout/revocation | user-service |
| F03 | RBAC on every endpoint; 403 on unauthorised access | all |
| F04 | Clients view/edit own profile; tellers edit any; changes logged | user-service |
| F05 | Tellers issue debit cards (PENDING → activated) | account-service |
| F06 | Block/unblock debit cards | account-service |
| F07 | Teller opens CHECKING / SAVINGS / FOREIGN accounts | account-service |
| F08 | Admin closes accounts (balance zero, no pending activity) | account-service |
| F09 | View balance: available, pending holds, status | account-service |
| F10 | Teller deposit / withdrawal with audit trail | transaction-service |
| F11 | Client transfers via IBAN; atomic; auto FX on foreign accounts | transaction-service |
| F12 | Paginated transaction history with filters | transaction-service |
| F13 | Client submits loan application; tracks status | loan-service |
| F14 | Loan officer approves/rejects; disbursement via RabbitMQ | loan-service |
| F15 | Client views repayment schedule and remaining balance | loan-service |
| F16 | Statement generation with running balance; PDF download | transaction-service |
| F17 | Admin dashboard: total clients, daily txn count, weekly volume | user-service / transaction-service |

---

## API Documentation

Swagger UI is available on each service while it is running:

| Service | URL |
|---|---|
| user-service | http://localhost:8081/swagger-ui.html |
| account-service | http://localhost:8082/swagger-ui.html |
| transaction-service | http://localhost:8083/swagger-ui.html |
| loan-service | http://localhost:8084/swagger-ui.html |

Via the gateway:

| Service | URL |
|---|---|
| user-service | http://localhost:8080/user-service/swagger-ui/index.html |
| account-service | http://localhost:8080/account-service/swagger-ui/index.html |

---

## Running Tests

```bash
# Unit + integration tests for a single service
cd user-service && ./mvnw verify

# Run only unit tests (no Testcontainers / Docker required)
cd user-service && ./mvnw test -Dgroups='!integration'
```

Integration tests use Testcontainers and require Docker to be running.

### Code Coverage

JaCoCo is configured on all five services. After `mvn verify` the HTML report is at:

```
<service>/target/site/jacoco/index.html
```

`DataLoader` and `*Application` bootstrap classes are excluded from measurement.

---

## Project Structure

```
nexus-bank/
├── api-gateway/
├── discovery-server/
├── user-service/
├── account-service/
├── transaction-service/
├── loan-service/
├── frontend/
└── compose-rabbitmq.yaml
```
