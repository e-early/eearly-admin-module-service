# eEarly Admin Service (OSS)

Open-source backend for running **health data analyses** against a hosted apnea detection model.

Supports:

- patients and caretakers
- measurement catalog (HR, SpO₂, …) + biometrics via **mobile gRPC**
- algorithms (hosted HTTP model, no BYO container)
- analyses with **time-range selections**
- algorithm executions (`POST /predict` on the hosted model)

Does **not** include organization/department/health-condition management, QR onboarding, email, or development simulation.

---

## Prerequisites

| Requirement | Version / note |
|-------------|----------------|
| Java | 21+ |
| Maven | 3.9+ |
| Docker | admin Postgres; also EHR + mobile infra |
| Nexus | `https://nexus.result.si` (parent POM + deps) |
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
export APNEA_SERVICE_ENDPOINT=http://10.10.10.78:3001

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
Mobile GET /onboarding/{userId}/configuration  → patient JWT
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

Extract Keycloak user id from patient response (do **not** use `sed 's|.*/||'` on the full URL):

```bash
export PATIENT_ID=$(jq -r '.payload.id' /tmp/patient.json)
export USER_ID=$(jq -r '.payload.onboardingUrl' /tmp/patient.json \
  | sed -E 's|.*/onboarding/||; s|/configuration$||')
```

See the full guide for template upload, ingest scripts, env vars, and success criteria.

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

VPN / access to `https://nexus.result.si`.

---

## Related repositories

- [eearly-mobile-module-service-opensource](https://github.com/e-early/eearly-mobile-module-service.git) — measurements gateway + Keycloak realm
- [eearly-ehr-module-opensource](https://github.com/e-early/eearly-ehr-module-service.git) — EHRbase + EHR Keycloak

---