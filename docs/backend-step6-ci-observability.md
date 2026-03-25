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
  - `e2e-gateway-core-payment` job:
    - build/start compose stack
    - chay smoke E2E script `scripts/e2e-gateway-core-payment.ps1`
    - dump compose logs khi fail
    - teardown stack sau khi chay

## Muc dich

- Chan merge neu build hoac test cot loi fail.
- Dam bao thay doi tren mot service khong vo tinh lam hong service khac.
- Giu release gate co test flow thuc te qua gateway cho booking + payment callback.

## Buoc tiep theo de dat muc production day du

- Them quality gate static analysis (SpotBugs/PMD/Checkstyle/Sonar).
- Metrics/alerting cap he thong da duoc trien khai tai `docs/backend-step7-observability.md`.
