# Backend Step 6 - CI gate toi thieu

## Da them

- GitHub Actions workflow: `.github/workflows/backend-ci.yml`
  - `compile` job:
    - matrix compile cho 7 service backend.
  - `fast-tests` job:
    - chay bo test nhanh/cot loi:
      - `core-service`: `BookingServiceIntegrationTest`, `BookingLifecycleSchedulerTest`
      - `auth-service`: `AuthServiceIntegrationTest`
      - `payment-service`: `PaymentServiceIntegrationTest`
      - `api-gateway`: `GatewaySecurityIntegrationTest`

## Muc dich

- Chan merge neu build hoac test cot loi fail.
- Dam bao thay doi tren mot service khong vo tinh lam hong service khac.
- Giu chu ky feedback ngan (chi chay bo test nhanh, bo test E2E full flow de chay rieng).

## Buoc tiep theo de dat muc production day du

- Them bo E2E workflow rieng (can Docker/Testcontainers san sang trong CI runner).
- Them quality gate static analysis (SpotBugs/PMD/Checkstyle/Sonar).
- Them metrics/alerting o cap he thong (Prometheus + Grafana + alert rule cho outbox pending/failed).
