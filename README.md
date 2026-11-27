# eEarly Admin Service (OSS)

Open-source backend for running **health data analyses** against a hosted apnea detection model.

Supports:

- patients and caretakers
- measurement catalog (HR, SpO₂, …) + biometrics via **mobile gRPC**
- algorithms (hosted HTTP model, no BYO container)
- analyses with **time-range selections**
- algorithm executions (`POST /predict` on the hosted model)

- **QR code onboarding** (`/api/v1/qrcodes`, see [QR code onboarding](#qr-code-onboarding))

Does **not** include organization/department/health-condition management. Algorithm executions can be simulated
without algorithm containers with the `simulation` profile (see [Algorithm simulation](#algorithm-simulation)).
The QR code message is sent through the e-mail infrastructure (see [E-mail](#e-mail)).

---

## Prerequisites

| Requirement | Version / note |
|-------------|----------------|
| Java | 21+ |
| Maven | 3.9+ |
| Docker | admin Postgres; also EHR + mobile infra |
| Nexus | `https://nexus.result.si` (parent POM + internal `si.result.lib` deps); required to build, see below |
| `grpcurl` | for ingesting measurements into mobile (optional install below) |
| `jq` | helpful for curl responses |

### Related services (local E2E)

| Service | How to run | Host ports |
|---------|------------|------------|
| **EHRbase** | `eearly-ehr-module-opensource` → `docker compose up -d` | `8000` (API), `9092` (EHR Keycloak) |
| **Mobile infra** | `eearly-mobile-module-service-opensource` → compose **without** app container | DB `5433`, Keycloak `9091` |
| **Mobile app** | Maven on host (`mvn -pl eearly -am spring-boot:run`) | REST `8081`, gRPC `8082` |
| **Admin DB** | this repo → `etc/eearly-admin/docker-compose.yaml` | `5432` |
| **Admin app** | this repo → JAR with `development,unrestricted` | `9000` |
| **Apnea model** | hosted / acceptance | `3001` (`POST /predict`) |

Mobile app runs on the **host** (not in Docker) so `localhost` reaches EHRbase. Prefer:

```bash
docker compose up -d eearly-mobile-keycloak-db eearly-mobile-keycloak eearly-mobile-db
```

Install `grpcurl` (if needed):

```bash
curl -sL https://github.com/fullstorydev/grpcurl/releases/download/v1.9.3/grpcurl_1.9.3_linux_x86_64.tar.gz \
  | sudo tar -xz -C /usr/local/bin grpcurl
```

---

## Quick start — admin only

### 1. PostgreSQL

```bash
cd /path/to/eearly-admin-module-service-opensource
docker compose -f etc/eearly-admin/docker-compose.yaml up -d
```

Defaults (`application-development.yaml`):

- `localhost:5432` / DB `eearly-admin_db` / user+password `eearly-admin`

Fresh DB (Flyway checksum / old migrations):

```bash
docker compose -f etc/eearly-admin/docker-compose.yaml down -v
docker compose -f etc/eearly-admin/docker-compose.yaml up -d
```

### 2. Environment

Export before starting the JAR (Spring does **not** auto-load `.env`):

```bash
export MOBILE_SERVICE_BASE_URL=http://localhost:8081/api/v1
export MOBILE_KEYCLOAK_BASE_URL=http://localhost:9091
export MOBILE_KEYCLOAK_REALM=eearly-mobile
export MOBILE_KEYCLOAK_ADMIN_CLIENT_SECRET=JTAk6BzrwcIumfUVh6TssRUbU7wTTZl5
export GRPC_ADDRESS=static://localhost:8082

# Hosted model (acceptance example). Local mock: http://localhost:3001
export APNEA_SERVICE_ENDPOINT=http://<apnea-service-host>:3001

# Required when the model returns HTTP 401 "Authorization header missing"
export ALGORITHM_BASIC_AUTH_USERNAME=eearly-bentoml_auth_user
export ALGORITHM_BASIC_AUTH_PASSWORD='<plaintext from ansible vault>'
```

| Variable | Local OSS value | Purpose |
|----------|-----------------|--------|
| `MOBILE_SERVICE_BASE_URL` | `http://localhost:8081/api/v1` | Create Keycloak user on patient create |
| `MOBILE_KEYCLOAK_BASE_URL` | `http://localhost:9091` | Mobile realm |
| `MOBILE_KEYCLOAK_ADMIN_CLIENT_SECRET` | from mobile `realm-config` → client `admin-service` | Patient create + gRPC admin token |
| `GRPC_ADDRESS` | `static://localhost:8082` | `GetMeasurementsForUser` |
| `APNEA_SERVICE_ENDPOINT` | model base URL (no `/predict`) | Seed + runtime endpoint |
| `ALGORITHM_BASIC_AUTH_*` | vault-decrypted for acceptance | Basic auth on `/predict` |

### 3. Build

Use **`-Dmaven.test.skip=true`** (not only `-DskipTests`). Test compile currently fails on an outdated `PatientServiceTest`.

```bash
mvn clean package -Dmaven.test.skip=true
```

### 4. Run

```bash
java -jar eearly-admin-service/target/eearly-admin-service.jar \
  --spring.profiles.active=development,unrestricted
```

- API: `http://localhost:9000/api/v1`
- Swagger: `http://localhost:9000/swagger-ui/index.html`
- Health: `http://localhost:9000/actuator/health`

Profile `unrestricted` = no JWT required on admin REST (local testing).

### Algorithm simulation

Profile `simulation` runs analyses without any algorithm container. It replaces the HTTP adapter to the
algorithm containers with a stand-in that, a few seconds after an execution is created, stores two fixed demo
detections on the linked analysis (state `WAITING_FOR_CONFIRMATION`) and completes the execution.

```bash
SPRING_PROFILES_ACTIVE=development,unrestricted,simulation java -jar eearly-admin-service/target/eearly-admin-service.jar
```

- **The results are fabricated demo data, not the output of a model. Never use this profile with real patients
  or for clinical decisions.** A warning is logged at startup.
- Executions completed by the simulation carry the result data `{"source":"simulation"}`. The analysis itself and its
  detections are not marked, so do not mix simulated and real data in one database.
- Only executions linked to an analysis are simulated; an execution without one is marked as failed.
- `simulation.delay-ms` (default `3000`) sets the simulated processing time.
- Without the profile nothing changes: the real adapter is used and no simulation bean exists.

---

## Seed data (`development` profile)

Flyway loads `db/migration` + `db/test-data`.

| Entity | UUID |
|--------|------|
| Caretaker | `987fcdeb-51d2-45e6-8b1a-23456789abcd` |
| Apnea algorithm | `8598bfe7-ce24-4b33-a394-b73c37fb2208` |
| Heart rate (catalog) | `fedcba98-7654-3210-fedc-ba9876543212` |
| Blood oxygen (catalog) | `fedcba98-7654-3210-fedc-ba9876543216` |
| Demo patient (John Smith) | `f47ac10b-58cc-4372-a567-0e02b2c3d479` |

**Do not use the seed patient for a real E2E run.** Its `keycloak_id` is a placeholder and does not exist in mobile Keycloak. Create a **new** patient (step 2 below).

Algorithm: `ACTIVE`, run path `/predict`, `runner_config` `{"step_size": 15}`.  
`health_check_endpoint` is `NULL` → `GET .../service-status` returns *“health check is not configured”* (expected; execution still works).

```bash
curl -s http://localhost:9000/api/v1/algorithms/8598bfe7-ce24-4b33-a394-b73c37fb2208 \
  | jq '.payload | {name, serviceEndpoint, runEndpoint, healthCheckEndpoint}'
```

---

## Full apnea analysis flow (OSS E2E)

**Step-by-step guide with every command:** [`docs/E2E_APNEA_FLOW.md`](docs/E2E_APNEA_FLOW.md)

That document covers infrastructure, mobile + admin startup, patient creation, **separate HR/SpO₂ ingest**, biometric checks, analysis, execution, and troubleshooting (including correct `USER_ID` extraction from `onboardingUrl`).

Summary:

```text
EHR templates ready
        ↓
Admin POST /patients          → admin DB + mobile Keycloak user + EHR
        ↓
Mobile GET /onboarding/{code}/configuration  → patient JWT (single-use link)
        ↓
gRPC CreateMeasurement (HR)   → EHRbase
gRPC CreateMeasurement (SpO₂)   → EHRbase   ← two calls, two batch IDs
        ↓
Admin GET /biometric-records    → verify HR + SpO₂ (SpO₂ ≠ 0)
        ↓
Admin POST /analyses          → selections (time range + catalog measurement IDs)
        ↓
Admin POST /algorithm-executions
        ↓
  GetMeasurementsForUser (gRPC) → build payload → POST {APNEA}/predict
        ↓
GET /analyses/{id}            → COMPLETED + detections
```

Quick sanity checks before the flow:

```bash
curl -s http://localhost:8000/ehrbase/rest/status
curl -s http://localhost:9091/realms/eearly-mobile/.well-known/openid-configuration | head -c 80; echo
curl -s http://localhost:9000/actuator/health
```

Redeem the patient's single-use onboarding link once and keep the response:

```bash
export PATIENT_ID=$(jq -r '.payload.id' /tmp/patient.json)
curl -s "$(jq -r '.payload.onboardingUrl' /tmp/patient.json)/configuration" > /tmp/onboarding.json
export ACCESS_TOKEN=$(jq -r '.accessToken' /tmp/onboarding.json)
export USER_ID=$(jq -r '.userId' /tmp/onboarding.json)
```

See the full guide for template upload, ingest scripts, env vars, and success criteria.

---

## E-mail

The service sends e-mail (currently the patient QR code message) through an SMTP server, on a separate thread pool.
A failed send is logged and never fails the request. Logs contain only the message type and a random message id,
never recipients, names, codes or message bodies.

For local development run a catch-all SMTP server, for example
`docker run --rm -p 1025:1025 -p 8025:8025 axllent/mailpit`, and start the service with the `development` profile
(SMTP `localhost:1025`; read the messages at `http://localhost:8025`).

| Variable | Default | Purpose |
|----------|---------|---------|
| `MAIL_HOST` | `localhost` | SMTP host |
| `MAIL_PORT` | `25` (`development`: `1025`) | SMTP port |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | empty | SMTP credentials |
| `MAIL_SMTP_AUTH` | `false` | authenticate against the server |
| `MAIL_SMTP_STARTTLS` | `false` | upgrade to TLS with STARTTLS |
| `MAIL_SMTP_SSL` | `false` | TLS from the start (implicit TLS, usually port 465) |
| `MAIL_CONNECTION_TIMEOUT_MS`, `MAIL_TIMEOUT_MS` | `10000`, `60000` | connect and read/write timeouts |
| `EMAIL_FROM` | `noreply@example.org` | sender address of every message |
| `EMAIL_APP_NAME` | `eEarly` | sender name and application name in the text |
| `EMAIL_ALLOWED_RECIPIENT_DOMAINS` | `*` | comma-separated recipient domains; others are skipped (use on test systems) |
| `EMAIL_APP_DOWNLOAD_URL` | empty | http(s) link to the mobile app, shown in the QR code message; empty omits it |
| `EMAIL_TEMPLATE_FOLDER` | `classpath:templates/email/` | folder of the templates (`classpath:` or `file:`) |
| `EMAIL_TEMPLATE` | `qr-code-email.html` | QR code template (Slovenian, the default language) |
| `EMAIL_LOGO` | `classpath:images/email-logo.png` | logo in the messages; empty sends none |

**Languages.** A patient's language (`sl` default, or `en`) selects the template: `EMAIL_TEMPLATE` for Slovenian and
the same name with `-en` before the extension (`qr-code-email-en.html`) for English.

**Branding.** The bundled templates and logo are neutral. To brand the messages, copy `templates/email/` from
`eearly-admin-service/src/main/resources` to a folder of your own, edit the HTML and the logo, and point
`EMAIL_TEMPLATE_FOLDER` and `EMAIL_LOGO` at them, for example `file:/etc/eearly/email-templates/`. Placeholders
are written `{{NAME}}`: `APP_NAME`, `PATIENT_NAME`, `CARETAKER_NAME`, `QR_CODE_IMAGE`, `APP_DOWNLOAD_LINK` and
`LOGO`. Values are HTML-escaped.

---

## QR code onboarding

A caretaker creates a QR code for a patient assigned to them; the patient scans it (or receives it by e-mail) to
set up the mobile app. Each code holds a random token and a single-use onboarding link that the mobile service
issues for the patient when the code is created; the patient must already have a mobile user (created with the
patient). Creating a new code asks the mobile service for a new link, which invalidates the patient's earlier
links, and revokes the patient's earlier active codes. A code is valid for the validity of the links
(`ONBOARDING_CODE_VALIDITY`, 30 days by default); a code that is used, revoked or expired cannot be sent by e-mail.

| Method and path | Purpose |
|---|---|
| `POST /api/v1/qrcodes` | create a code for `patientId`; 201. `onboardingUrl` and `caretakerId` in the request are ignored |
| `GET /api/v1/qrcodes` | page of the codes of the caller's patients |
| `GET /api/v1/qrcodes/{id}` | one code |
| `GET /api/v1/qrcodes/patient/{id}` | the latest code of a patient |
| `POST /api/v1/qrcodes/send-email` | send the code (`qrCodeId`, `appName`) to the patient's own e-mail address, in the patient's language |

Access: an authenticated caller who is a caretaker, and only for patients assigned to that caretaker. The
recipient of the e-mail is always the patient's address; the request cannot choose one. Requests without a token
get 401.

| Variable | Default | Meaning |
|---|---|---|
| `QR_CODE_LINK_BASE_URL` | empty | app-link page the code points at; empty encodes the issued onboarding link itself |
| `ONBOARDING_CODE_VALIDITY` | `P30D` | validity of a code, an ISO-8601 duration; keep it equal to the onboarding link validity of the mobile service |
| `QR_CODE_ALLOW_HTTP_ONBOARDING_URL` | `false` (`true` with the `development` profile) | accept a plain `http` onboarding link; otherwise only `https` links of at most 512 characters are accepted |
| `EMAIL_LOGO` | `classpath:images/email-logo.png` | logo drawn in the middle of the code; empty gives a plain code |

Tokens and onboarding links are not logged. There is no rate limiting in the service; put it in front of the
endpoints if exposed.

---

## Project structure

```text
eearly-admin/
├── eearly-admin-service/              Spring Boot app (run this)
├── eearly-admin-rest-adapter/         REST + facades + HTTP model client
├── eearly-admin-domain/               domain model + ports
├── eearly-admin-persistence-adapter/  JPA + Flyway
└── eearly-admin-grpc-adapter/         mobile gRPC clients
```

- Migrations: `eearly-admin-persistence-adapter/src/main/resources/db/migration/`
- Dev seed: `eearly-admin-persistence-adapter/src/main/resources/db/test-data/`

---

## Troubleshooting

### Flyway checksum / `1.0.0` not resolved

Reset admin DB:

```bash
docker compose -f etc/eearly-admin/docker-compose.yaml down -v
docker compose -f etc/eearly-admin/docker-compose.yaml up -d
```

### Build fails on `PatientServiceTest`

```bash
mvn clean package -Dmaven.test.skip=true
```

### `HTTP 401: Authorization header missing` on execution

Model requires auth. Set `ALGORITHM_BASIC_AUTH_USERNAME` / `ALGORITHM_BASIC_AUTH_PASSWORD` (or `BENTOML_API_KEY`), **restart** admin, then create a **new** execution (same `ANALYSIS_ID` is fine).

Quick probe:

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST "${APNEA_SERVICE_ENDPOINT}/predict" \
  -H 'Content-Type: application/json' -d '{}'
# 401 without auth

curl -s -o /dev/null -w "%{http_code}\n" -X POST "${APNEA_SERVICE_ENDPOINT}/predict" \
  -H 'Content-Type: application/json' \
  -u "${ALGORITHM_BASIC_AUTH_USERNAME}:${ALGORITHM_BASIC_AUTH_PASSWORD}" \
  -d '{}'
# not 401 if credentials are correct (422 possible for empty body)
```

### Empty measurements / preparation failed

- Mobile gRPC reachable at `GRPC_ADDRESS`
- Patient has a **real** `keycloak_id` (create via `POST /patients`)
- Measurements exist in the **same time window** as analysis selections
- OpenEHR templates uploaded to EHRbase
- HR and SpO₂ ingested in **separate** `CreateMeasurement` calls (see [`docs/E2E_APNEA_FLOW.md`](docs/E2E_APNEA_FLOW.md))

### SpO₂ shows 0 % in biometrics or model rejects 50–100 % range

SpO₂ is stored as openEHR **proportion**; mobile must read `numerator` in AQL. Rebuild mobile OSS from current sources and restart before re-testing.

### `service-status` unhealthy

Seed has no `healthCheckEndpoint`. Ignore for E2E, or set one (e.g. `/healthz`) on the algorithm if the model exposes it.

### Patient create fails / Keycloak errors

Check `MOBILE_SERVICE_BASE_URL` (`8081`), `MOBILE_KEYCLOAK_BASE_URL` (`9091`), and `MOBILE_KEYCLOAK_ADMIN_CLIENT_SECRET`.

### Admin REST 401

Use profile `unrestricted`, or configure `JWK_SET_URI` and send a Bearer token.

### Nexus / Maven resolve failure

This project's internal `si.result.lib` libraries (including the parent POM) are resolved from
Result's Nexus (`https://nexus.result.si/repository/result-lib/`, declared in the root `pom.xml`)
until they are published publicly, so the build needs access to it (VPN). Public artifacts
come from Maven Central; if you hit a resolve failure for those, retry or check your network.

---

## Related repositories

- [eearly-mobile-module-service-opensource](https://github.com/e-early/eearly-mobile-module-service.git) — measurements gateway + Keycloak realm
- [eearly-ehr-module-opensource](https://github.com/e-early/eearly-ehr-module-service.git) — EHRbase + EHR Keycloak

---