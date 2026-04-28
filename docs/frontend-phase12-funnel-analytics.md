# Frontend Phase 12 - Funnel Tracking and Smoke Validation

Ngay cap nhat: 2026-04-23

## Muc tieu
- Do duoc hanh vi chinh cua funnel dat san:
  - landing -> discover -> booking grid -> checkout -> payment.
- Co bo smoke test tu dong de kiem tra nhanh sau moi lan doi frontend.

## Event analytics da gan

Nguon: `frontend/src/lib/analytics.ts` (`trackEvent`) day vao `window.dataLayer`
va phat custom event `sportcourt:analytics`.

### Landing
- `funnel_landing_search_submit`
- `funnel_landing_explore_click`
- `funnel_landing_auth_cta_click`
- `funnel_landing_quick_link_click`

File: `frontend/src/pages/LandingPage.tsx`

### Discover
- `funnel_discover_book_click`
- `funnel_discover_clear_filters`
- `funnel_discover_map_click`

File: `frontend/src/pages/customer/DiscoverPage.tsx`

### Booking Grid
- `funnel_grid_slot_range_selected`
- `funnel_grid_continue_checkout`
- `funnel_grid_selection_cleared`

File: `frontend/src/pages/customer/BookingGridPage.tsx`

### Checkout
- `funnel_checkout_view`
- `funnel_checkout_redirect_login`
- `funnel_checkout_draft_created`
- `funnel_checkout_draft_failed`

File: `frontend/src/pages/customer/BookingCheckoutPage.tsx`

### Payment
- `funnel_payment_view`
- `funnel_payment_booking_status`
- `funnel_payment_initiated`
- `funnel_payment_initiate_failed`
- `funnel_payment_callback_simulated_success`
- `funnel_payment_callback_simulation_failed`
- `funnel_payment_success`

File: `frontend/src/pages/customer/PaymentPage.tsx`

## Smoke test tu dong

### Local
- Chay stack backend (gateway + auth/core/payment + db + kafka).
- Chay command:
  - `cd frontend`
  - `npm run phase12:smoke`

Test file:
- `frontend/tests/funnel-smoke.spec.ts`

Flow test:
1. Dang ky customer test.
2. Tu landing submit quick search.
3. Mo discover va bam Dat lich.
4. O booking grid chon slot hop le.
5. Sang checkout tao draft.
6. O payment tao transaction + simulate callback success.
7. Assert day du event trong `window.dataLayer`.

### CI
- Workflow: `.github/workflows/frontend-ci.yml`
- Job: `phase12-funnel-smoke`
- Trinh tu:
  1. Build + start compose stack.
  2. Wait `http://localhost:8080/actuator/health`.
  3. Run `npm run phase12:smoke`.
  4. Dump compose logs neu fail.

## Luu y tich hop
- Event payload chi giu metadata can thiet, khong chua secret.
- Cac action write trong funnel nen gui idempotency key o layer API de tranh tao du lieu trung khi retry.

## Tai lieu bo tro phase 12
- QA manual checklist: `docs/frontend-phase12-qa-manual-checklist.md`
- Pre-release review script: `docs/frontend-pre-release-review.md`
- Frontend DoD: `docs/frontend-definition-of-done.md`
