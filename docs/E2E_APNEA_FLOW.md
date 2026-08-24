# OSS end-to-end: apnea analysis (step-by-step)

This guide walks an **external OSS integrator** through a full local run: infrastructure → patient → measurements in EHRbase → analysis → ML model → detections.

There is **no bundled mobile app**. You use `curl` and `grpcurl` like a patient client would use gRPC.

**Related repositories**

| Repository | Role |
|------------|------|
| [eearly-ehr-module-opensource](https://git.result.si/eearly/eearly-ehr-module-opensource) | EHRbase + EHR Keycloak |
| [eearly-mobile-module-service-opensource](https://git.result.si/eearly/eearly-mobile-module-service-opensource) | Measurement gateway (REST + gRPC → EHRbase) |
| **This repo** (admin OSS) | Patients, analyses, algorithm execution, biometrics API |

---

## What happens in one successful run

```text
  [You]                    [Admin :9000]              [Mobile :8081/:8082]           [EHRbase :8000]
    |                              |                            |                          |
    |  POST /patients              |                            |                          |
    |----------------------------->|  POST create-user          |                          |
    |                              |--------------------------->|  Keycloak user + EHR     |
    |                              |                            |------------------------->|
    |  onboardingUrl               |                            |                          |
    |<-----------------------------|                            |                          |
    |  GET .../configuration       |                            |                          |
    |----------------------------------------------------------->|  patient JWT             |
    |  gRPC CreateMeasurement (HR) |                            |                          |
    |----------------------------------------------------------->|  write composition       |
    |                              |                            |------------------------->|
    |  gRPC CreateMeasurement (SpO₂)|                           |                          |
    |----------------------------------------------------------->|  write composition       |
    |                              |                            |------------------------->|
    |  POST /analyses              |                            |                          |
    |----------------------------->|                            |                          |
    |  POST /algorithm-executions    |                            |                          |
    |----------------------------->|  gRPC GetMeasurementsForUser |                          |
    |                              |--------------------------->|  AQL read                |
    |                              |                            |------------------------->|
    |                              |  POST {model}/predict      |                          |
    |                              |-----------------------------------------------------> VPN
    |  GET /analyses/{id}          |                            |                          |
    |<-----------------------------|  COMPLETED + detections    |                          |
```

**Success looks like**

- `executionStatus`: `COMPLETED`, `errorMessage`: null  
- Analysis `state`: `COMPLETED`, `detected`: true/false (model-dependent)  
- SpO₂ biometrics in range **50–100 %**, not `0.0`

---

## Prerequisites

| Tool | Notes |
|------|--------|
| Java 21+, Maven 3.9+ | Build mobile + admin on the host |
| Docker Compose | EHR, mobile DB/KC, admin Postgres |
| `curl`, `jq` | Admin REST |
| `grpcurl` | Ingest measurements (see admin README) |
| VPN (if needed) | Reach hosted acceptance model (e.g. `10.10.10.78:3001`) |
| Nexus access | Admin build may need `https://nexus.result.si` |

---

## Phase 1 — Start infrastructure

Run these once per machine (or after `docker compose down -v`).

### 1.1 EHRbase

```bash
cd /path/to/eearly-ehr-module-opensource
docker compose up -d
curl -s http://localhost:8000/ehrbase/rest/status
```

Wait until EHR Keycloak is ready (`9092`).

### 1.2 OpenEHR templates (once per EHR volume)

From the **mobile OSS** repo:

```bash
cd /path/to/eearly-mobile-module-service-opensource/eearly-common/src/main/resources/templates
for f in *.opt; do
  sed '1s/^\xEF\xBB\xBF//;s/\r$//' "$f" | \
  curl -s -o /dev/null -w "$f -> HTTP %{http_code}\n" \
    -X POST "http://localhost:8000/ehrbase/rest/openehr/v1/definition/template/adl1.4" \
    -H "Content-Type: application/xml" \
    --data-binary @-
done
```

Expect HTTP **201** or **409** (already uploaded).

### 1.3 Mobile PostgreSQL + Keycloak (not the Java app)

```bash
cd /path/to/eearly-mobile-module-service-opensource
docker compose up -d eearly-mobile-keycloak-db eearly-mobile-keycloak eearly-mobile-db
curl -s http://localhost:9091/realms/eearly-mobile/.well-known/openid-configuration | head -c 80; echo
```

### 1.4 Admin PostgreSQL

```bash
cd /path/to/eearly-admin-module-service-opensource
docker compose -f etc/eearly-admin/docker-compose.yaml up -d
```

**Fresh admin database** (Flyway errors / clean rerun):

```bash
docker compose -f etc/eearly-admin/docker-compose.yaml down -v
docker compose -f etc/eearly-admin/docker-compose.yaml up -d
```

---

## Phase 2 — Build and run applications

The mobile app runs **on the host** (not in Docker) so it can reach `localhost:8000` (EHRbase).

### 2.1 Mobile service

```bash
cd /path/to/eearly-mobile-module-service-opensource
mvn clean package -pl eearly -am -Dmaven.test.skip=true

set -a && source .env.example && set +a
java -jar eearly/target/eearly.jar
```

Leave this terminal open.

| Port | Service |
|------|---------|
| `8081` | REST (`/api/v1/onboarding/...`) |
| `8082` | gRPC (`MeasurementService`) |

### 2.2 Admin service (second terminal)

```bash
cd /path/to/eearly-admin-module-service-opensource
mvn clean package -Dmaven.test.skip=true

export MOBILE_SERVICE_BASE_URL=http://localhost:8081/api/v1
export MOBILE_KEYCLOAK_BASE_URL=http://localhost:9091
export MOBILE_KEYCLOAK_REALM=eearly-mobile
export MOBILE_KEYCLOAK_ADMIN_CLIENT_SECRET=JTAk6BzrwcIumfUVh6TssRUbU7wTTZl5
export GRPC_ADDRESS=static://localhost:8082

# Model base URL — no trailing /predict
export APNEA_SERVICE_ENDPOINT=http://10.10.10.78:3001

# Required if the model returns HTTP 401 without auth
export ALGORITHM_BASIC_AUTH_USERNAME=eearly-bentoml_auth_user
export ALGORITHM_BASIC_AUTH_PASSWORD='<plaintext password from your team / Ansible vault>'

java -jar eearly-admin-service/target/eearly-admin-service.jar \
  --spring.profiles.active=development,unrestricted
```

Profile **`unrestricted`** disables JWT on admin REST for local testing.

```bash
curl -s http://localhost:9000/actuator/health
```

---

## Phase 3 — Reference IDs (seed data)

With profile `development`, Flyway loads test data. Use these constants in the commands below:

```bash
export CARETAKER_ID=987fcdeb-51d2-45e6-8b1a-23456789abcd
export ALGORITHM_ID=8598bfe7-ce24-4b33-a394-b73c37fb2208

# Admin measurement catalog (analyses / selections)
export HR_CATALOG_ID=fedcba98-7654-3210-fedc-ba9876543212
export SPO2_CATALOG_ID=fedcba98-7654-3210-fedc-ba9876543216

# Mobile measurement types (gRPC CreateMeasurement only)
export HR_TYPE_ID=3d51f149-4eb2-42af-b217-8f5a754968eb
export SPO2_TYPE_ID=0ecc92b4-dd67-4ed0-952d-cbbd05026332

export MOBILE_KEYCLOAK_ADMIN_CLIENT_SECRET=JTAk6BzrwcIumfUVh6TssRUbU7wTTZl5
```

| ID | Do **not** use for E2E |
|----|-------------------------|
| Seed patient `f47ac10b-58cc-4372-a567-0e02b2c3d479` (John Smith) | Placeholder Keycloak id — create a **new** patient |

Verify apnea algorithm seed:

```bash
curl -s "http://localhost:9000/api/v1/algorithms/${ALGORITHM_ID}" \
  | jq '.payload | {name, serviceEndpoint, runEndpoint, status}'
```

---

## Phase 4 — Create patient (admin)

`POST /patients` creates a row in admin DB **and** a real user in mobile Keycloak + EHR via mobile REST.

Use a **unique** `healthInsuranceId` and `email` on each run:

```bash
curl -s -X POST http://localhost:9000/api/v1/patients \
  -H 'Content-Type: application/json' \
  -d "{
    \"firstName\": \"Test\",
    \"lastName\": \"Apnea\",
    \"dateOfBirth\": \"1990-05-10\",
    \"gender\": \"FEMALE\",
    \"healthInsuranceId\": \"HI$(date +%s)\",
    \"street\": \"Testna 1\",
    \"city\": \"Ljubljana\",
    \"state\": \"OS\",
    \"zip\": 1000,
    \"country\": \"Slovenia\",
    \"email\": \"apnea.$(date +%s)@example.com\",
    \"phoneNumber\": \"+38640111222\",
    \"caretakerList\": [\"${CARETAKER_ID}\"],
    \"measurementList\": [\"${HR_CATALOG_ID}\", \"${SPO2_CATALOG_ID}\"],
    \"algorithmsList\": [\"${ALGORITHM_ID}\"]
  }" | tee /tmp/patient.json | jq .
```

Save IDs from the response:

```bash
export PATIENT_ID=$(jq -r '.payload.id' /tmp/patient.json)
export USER_ID=$(jq -r '.payload.onboardingUrl' /tmp/patient.json \
  | sed -E 's|.*/onboarding/||; s|/configuration$||')
echo "PATIENT_ID=$PATIENT_ID"
echo "USER_ID=$USER_ID"
```

> **Important:** `onboardingUrl` ends with `/configuration`. Extract the UUID **between** `/onboarding/` and `/configuration`.  
> Do **not** use `sed 's|.*/||'` — that leaves the literal string `configuration` as `USER_ID`.

Example `onboardingUrl`:

`http://localhost:8081/api/v1/onboarding/8e5ad47e-77c0-4e4f-99ad-666eef8f766b/configuration`

---

## Phase 5 — Patient JWT (mobile REST)

```bash
curl -s "http://localhost:8081/api/v1/onboarding/${USER_ID}/configuration" | jq .

export ACCESS_TOKEN=$(curl -s \
  "http://localhost:8081/api/v1/onboarding/${USER_ID}/configuration" \
  | jq -r '.accessToken')
```

If `jq` fails, print the raw response — wrong `USER_ID` or mobile not running on `8081`.

---

## Phase 6 — Ingest heart rate and SpO₂ (gRPC)

### Rules

1. **`measuredAt`** must be `yyyy-MM-dd HH:mm:ss.SSS` (not ISO-8601 with `T` / `Z`).
2. **One measurement type per batch:** mobile writes one OpenEHR composition per `measurementBatchId`, using only the **first** measurement type in that batch. Send **HR and SpO₂ in two separate** `CreateMeasurement` calls with **different** `measurementBatchId` values.
3. Align timestamps with the analysis window you will use in Phase 8.

Example window: **80 points**, 1 s apart, starting **2026-02-12 10:00:00 UTC**:

```bash
export START_EPOCH=$(date -d '2026-02-12 10:00:00 UTC' +%s)
export COUNT=80

# --- Heart rate only ---
JSON_HR='{"measurements":['
for i in $(seq 0 $((COUNT - 1))); do
  TS=$(date -u -d "@$((START_EPOCH + i))" '+%Y-%m-%d %H:%M:%S.000')
  HR=$((70 + (i % 15)))
  [ $i -gt 0 ] && JSON_HR+=','
  JSON_HR+="{\"measurementTypeId\":\"${HR_TYPE_ID}\",\"value\":${HR},\"measuredAt\":\"${TS}\",\"measurementBatchId\":\"hr-batch-1\"}"
done
JSON_HR+=']}'

grpcurl -plaintext \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -d "$JSON_HR" \
  localhost:8082 \
  si.result.eearly.genproto.MeasurementService/CreateMeasurement

# --- SpO₂ only ---
JSON_SPO2='{"measurements":['
for i in $(seq 0 $((COUNT - 1))); do
  TS=$(date -u -d "@$((START_EPOCH + i))" '+%Y-%m-%d %H:%M:%S.000')
  SPO2=$((94 + (i % 5)))
  [ $i -gt 0 ] && JSON_SPO2+=','
  JSON_SPO2+="{\"measurementTypeId\":\"${SPO2_TYPE_ID}\",\"value\":${SPO2},\"measuredAt\":\"${TS}\",\"measurementBatchId\":\"spo2-batch-1\"}"
done
JSON_SPO2+=']}'

grpcurl -plaintext \
  -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  -d "$JSON_SPO2" \
  localhost:8082 \
  si.result.eearly.genproto.MeasurementService/CreateMeasurement
```

Each call should return `{}` on success.

For a **second ingest** on the same patient, change batch ids (e.g. `hr-batch-2`) and/or time range to avoid duplicate-timestamp issues in the model.

---

## Phase 7 — Verify biometrics (admin)

Admin reads measurements via mobile gRPC. REST path uses **`BLOOD_OXYGEN`** for SpO₂ (not `OXYGEN_SATURATION`).

```bash
export WIN_START=2026-02-12T10:00:00Z
export WIN_END=2026-02-12T10:02:00Z

curl -s "http://localhost:9000/api/v1/biometric-records/${PATIENT_ID}/measurement/HEART_RATE?startDateTime=${WIN_START}&endDateTime=${WIN_END}" \
  | jq '.payload | {totalRecords, unit}'

curl -s "http://localhost:9000/api/v1/biometric-records/${PATIENT_ID}/measurement/BLOOD_OXYGEN?startDateTime=${WIN_START}&endDateTime=${WIN_END}" \
  | jq '.payload | {totalRecords, unit, sample: .dataPoints[0:3] | map({timestamp, value})}'
```

**Gate before running the algorithm**

| Check | Expected |
|-------|----------|
| HR `totalRecords` | Matches ingest count (e.g. 80), unit `BPM` |
| SpO₂ `totalRecords` | Same count, unit `%` |
| SpO₂ `value` | ~94–98, **not** `0.0` |

If SpO₂ is zero, rebuild mobile from current OSS sources (SpO₂ is stored as openEHR proportion; AQL must read `numerator`). Do not proceed to execution until biometrics look correct.

---

## Phase 8 — Create analysis (admin)

Use **catalog** UUIDs (`HR_CATALOG_ID`, `SPO2_CATALOG_ID`), not mobile type UUIDs.  
Selection window must **cover** ingested measurement times.

```bash
curl -s -X POST http://localhost:9000/api/v1/analyses \
  -H 'Content-Type: application/json' \
  -d "{
    \"name\": \"Apnea detection\",
    \"patientId\": \"${PATIENT_ID}\",
    \"algorithmId\": \"${ALGORITHM_ID}\",
    \"selections\": [{
      \"startTimestamp\": \"${WIN_START}\",
      \"endTimestamp\": \"${WIN_END}\",
      \"measurementIds\": [\"${HR_CATALOG_ID}\", \"${SPO2_CATALOG_ID}\"]
    }]
  }" | tee /tmp/analysis.json | jq .

export ANALYSIS_ID=$(jq -r '.payload.id' /tmp/analysis.json)
```

---

## Phase 9 — Trigger algorithm execution (admin)

```bash
curl -s -X POST http://localhost:9000/api/v1/algorithm-executions \
  -H 'Content-Type: application/json' \
  -d "{
    \"algorithmId\": \"${ALGORITHM_ID}\",
    \"patientId\": \"${PATIENT_ID}\",
    \"analysisId\": \"${ANALYSIS_ID}\",
    \"triggerType\": \"MANUAL\"
  }" | tee /tmp/execution.json | jq .

export EXECUTION_ID=$(jq -r '.payload.id' /tmp/execution.json)
```

Pipeline (async):

1. Fetch biometrics (`GetMeasurementsForUser` with patient Keycloak id)  
2. Build payload from algorithm `input_schema`  
3. `POST ${APNEA_SERVICE_ENDPOINT}/predict` (Basic auth if configured)  
4. Store detection intervals on the analysis  

---

## Phase 10 — Poll results

```bash
sleep 10

curl -s "http://localhost:9000/api/v1/algorithm-executions/${EXECUTION_ID}" \
  | jq '.payload | {executionStatus, errorMessage, startedAt, completedAt}'

curl -s "http://localhost:9000/api/v1/analyses/${ANALYSIS_ID}" \
  | jq '.payload | {state, detected, detections}'
```

| Outcome | Execution | Analysis |
|---------|-----------|----------|
| Success | `COMPLETED`, no `errorMessage` | `COMPLETED`, `detections` may be non-empty |
| Model/auth error | `FAILED`, read `errorMessage` | often `ERROR` |
| Bad data | `FAILED` (validation / pandas / timestamps) | `ERROR` |

Example detection (acceptance model):

```json
{
  "startTimestamp": "2026-02-12T10:00:00Z",
  "endTimestamp": "2026-02-12T10:00:07Z",
  "probability": 0.874065,
  "source": "MODEL_PREDICTION"
}
```

---

## Optional — gRPC verify as admin client

```bash
export ADMIN_TOKEN=$(curl -s -X POST \
  http://localhost:9091/realms/eearly-mobile/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=client_credentials' \
  -d 'client_id=admin-service' \
  -d "client_secret=${MOBILE_KEYCLOAK_ADMIN_CLIENT_SECRET}" \
  | jq -r '.access_token')

grpcurl -plaintext \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" \
  -d "{\"userId\":\"${USER_ID}\",\"measurementTypes\":[\"HEART_RATE\",\"OXYGEN_SATURATION\"],\"page\":0,\"size\":5}" \
  localhost:8082 \
  si.result.eearly.genproto.MeasurementService/GetMeasurementsForUser
```

---

## Troubleshooting

| Symptom | Likely cause | Action |
|---------|----------------|--------|
| `jq: parse error` on onboarding | Wrong `USER_ID` (`configuration`) | Fix `USER_ID` extraction (Phase 4) |
| `MEASUREMENT_NOT_FOUND` on patient create | Invalid catalog id in `measurementList` | Use seed catalog UUIDs |
| Only HR in EHR, no SpO₂ | HR + SpO₂ in same `measurementBatchId` | Two gRPC calls (Phase 6) |
| SpO₂ biometrics `0.0` | Old mobile / AQL reads `magnitude` only | Use current mobile OSS + rebuild |
| `HTTP 401` on execution | Missing model Basic auth | Set `ALGORITHM_BASIC_AUTH_*`, restart admin |
| `totalRecords: 0` | Analysis window ≠ ingest times | Align `WIN_START` / `WIN_END` |
| Execution stuck `PENDING` | Admin/mobile down or DB issue | Logs on both services |
| Duplicate timestamps | Re-ingest same times | New patient or new time window |
| Patient create Keycloak error | Mobile/env | Check `8081`, `9091`, admin client secret |

### Model auth probe

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST "${APNEA_SERVICE_ENDPOINT}/predict" \
  -H 'Content-Type: application/json' -d '{}'
# 401 without credentials

curl -s -o /dev/null -w "%{http_code}\n" -X POST "${APNEA_SERVICE_ENDPOINT}/predict" \
  -H 'Content-Type: application/json' \
  -u "${ALGORITHM_BASIC_AUTH_USERNAME}:${ALGORITHM_BASIC_AUTH_PASSWORD}" \
  -d '{}'
# not 401 if credentials OK (422 on empty body is fine)
```

### Algorithm health endpoint

Seed algorithm has no `healthCheckEndpoint`. `GET .../service-status` may report health check not configured — **expected**; execution still works.

---

## Port summary

| Port | Service |
|------|---------|
| 8000 | EHRbase REST |
| 9092 | EHR Keycloak |
| 5433 | Mobile Postgres |
| 9091 | Mobile Keycloak |
| 8081 | Mobile REST |
| 8082 | Mobile gRPC |
| 5432 | Admin Postgres |
| 9000 | Admin REST + Swagger |

---

## Next steps for integrators

- Replace `grpcurl` loops with your patient client using protos in mobile OSS.  
- Point `APNEA_SERVICE_ENDPOINT` at your own model; keep `input_schema` in sync with admin algorithm seed.  
- For production: disable `unrestricted`, secure onboarding `create-user`, and use real secrets — not values from this guide.
