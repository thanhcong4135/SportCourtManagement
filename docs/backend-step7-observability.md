# Backend Step 7 - Observability stack (Prometheus + Grafana + Alertmanager)

## Muc tieu

- Co metrics he thong tap trung cho toan bo backend services.
- Co dashboard mac dinh de theo doi trang thai runtime.
- Co alert rule co ban cho service down, outbox failed, 5xx cao.

## Da trien khai

### 1) Metrics endpoint cho cac service

- Them dependency `io.micrometer:micrometer-registry-prometheus` vao tat ca service backend:
  - `services/api-gateway/pom.xml`
  - `services/auth-service/pom.xml`
  - `services/core-service/pom.xml`
  - `services/payment-service/pom.xml`
  - `services/notification-service/pom.xml`
  - `services/reporting-service/pom.xml`
  - `services/chatbot-service/pom.xml`
- Mo endpoint `/actuator/prometheus` trong `application.yml` cua tat ca service:
  - `management.endpoints.web.exposure.include=health,info,metrics,prometheus`

### 2) Security allowlist cho metrics endpoint

- Permit `/actuator/prometheus` cho cac service co security:
  - `services/core-service/src/main/java/com/sportcourt/core/config/SecurityConfig.java`
  - `services/payment-service/src/main/java/com/sportcourt/payment/config/SecurityConfig.java`
  - `services/auth-service/src/main/java/com/sportcourt/auth/config/SecurityConfig.java`
  - `services/api-gateway/src/main/java/com/sportcourt/gateway/config/SecurityConfig.java`

### 3) Stack monitoring trong Docker Compose

- Them 3 service:
  - `prometheus` (`prom/prometheus:v2.54.1`)
  - `grafana` (`grafana/grafana:11.3.0`)
  - `alertmanager` (`prom/alertmanager:v0.28.1`)
- File compose:
  - `infra/docker/docker-compose.yml`
- Config moi:
  - `infra/docker/observability/prometheus/prometheus.yml`
  - `infra/docker/observability/prometheus/alerts.yml`
  - `infra/docker/observability/alertmanager/alertmanager.yml`
  - `infra/docker/observability/grafana/provisioning/datasources/datasource.yml`
  - `infra/docker/observability/grafana/provisioning/dashboards/dashboard-provider.yml`
  - `infra/docker/observability/grafana/provisioning/dashboards/json/backend-overview.json`

### 4) Alert rules da co

- `BackendServiceDown` (critical)
- `OutboxFailedEventsDetected` (warning)
- `CoreOutboxPublishFailures` (warning)
- `High5xxErrorRate` (warning)

## Cach chay local

```powershell
docker compose --env-file .env -f infra/docker/docker-compose.yml up -d \
  mysql-core mysql-payment mysql-auth kafka \
  core-service payment-service auth-service api-gateway \
  notification-service reporting-service chatbot-service \
  prometheus alertmanager grafana
```

Neu da thay doi code service, dung `--build` cho nhom service Java:

```powershell
docker compose --env-file .env -f infra/docker/docker-compose.yml up -d --build \
  api-gateway auth-service core-service payment-service \
  notification-service reporting-service chatbot-service
```

## Verify nhanh

- Prometheus targets:
  - `http://localhost:9090/targets`
- Prometheus rules:
  - `http://localhost:9090/rules`
- Grafana:
  - `http://localhost:3000` (`admin/admin` mac dinh)
  - Dashboard auto provision: `SportCourt Backend Overview`
- Alertmanager:
  - `http://localhost:9093`

## Port env (co the doi trong .env)

- `PROMETHEUS_PORT=9090`
- `GRAFANA_PORT=3000`
- `ALERTMANAGER_PORT=9093`

## Ghi chu

- Day la baseline observability cho dev/staging.
- De production, can bo sung:
  - receiver that su (Slack/Email/PagerDuty) trong Alertmanager,
  - dashboard SLI/SLO theo nghiep vu,
  - retention/chinh sach luu tru phu hop tai nguyen.