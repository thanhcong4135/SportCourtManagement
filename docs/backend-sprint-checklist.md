# Backend Sprint Checklist (Priority-first)

Trang thai: Sprint 1-3 da implement. Sprint 4-5 tam hoan (de sau).

## Sprint 1 - Notification reliability (P0)
Muc tieu: chuyen `notification-service` tu mock sang event-driven production-ready.

- [x] Tao DB + Flyway cho notification (`notification_db`) va entity `notification_message`.
- [x] Consume Kafka events (`booking.events`, `payment.events`) va map sang template thong bao.
- [x] Them worker retry + backoff + dead-letter handling cho notify failures.
- [x] Luu lich su gui (QUEUED/SENT/FAILED) + traceId/eventId.
- [x] API query lich su thong bao theo booking/customer.
- [x] Test: integration test consumer + retry logic + idempotency.

Definition of Done:
- [x] Thong bao duoc gui theo event that.
- [x] Khong mat message khi service restart.
- [x] Co metric sent/failed/retry.

## Sprint 2 - Pricing + Sales + Reschedule (P0)
Muc tieu: hoan thien core nghiep vu doanh thu va booking lifecycle.

- [x] Module Pricing Rules (`pricing-rule`) theo time-window/day-type/customer-tier.
- [x] API quote gia: `GET /api/pricing/quote`.
- [x] Luu price snapshot khi confirm booking.
- [x] Module Products/Orders (add-on) + attach bookingId.
- [x] API reschedule booking (`/bookings/{id}/reschedule`) co overlap check + idempotency.
- [x] Test: unit + integration cho quote/snapshot/reschedule/conflict.

Definition of Done:
- [x] Gia booking truy vet duoc theo snapshot.
- [x] Add-on revenue tinh rieng, tong hop duoc theo booking.
- [x] Reschedule an toan, khong double-booking.

## Sprint 3 - Reporting read model that (P1)
Muc tieu: bo sung dashboard API dung du lieu that thay vi mock zero.

- [x] Tao reporting read model (daily occupancy, revenue, best-hours).
- [x] Event projection tu core/payment/sales sang reporting DB.
- [x] API: occupancy/revenue/best-hours theo branch/date-range.
- [x] Add pagination/filter/sort cho report endpoint.
- [x] Test projection idempotency + reconciliation check.

Definition of Done:
- [x] Bao cao tra du lieu that, khong hardcode.
- [x] Rebuild projection duoc khi can.

## Sprint 4 - Chatbot service nghiep vu that (P1)
Muc tieu: chatbot ho tro dat san thuc te va ton trong RBAC.

- [ ] Endpoint `POST /api/chatbot/booking-draft`.
- [ ] Permission-aware retrieval (customer chi thay du lieu cua minh).
- [ ] Goi core APIs that de check availability/quote.
- [ ] Add safety guardrails: read-only by default, action can xac nhan.
- [ ] Log intent/latency voi masking sensitive data.

Definition of Done:
- [ ] Chatbot tao duoc booking draft hop le.
- [ ] Khong lo thong tin cross-user.

## Sprint 5 - Platform hardening (P1)
Muc tieu: nang do ben va van hanh.

- [ ] Gateway rate limit + CORS policy chuan hoa.
- [ ] Redis cache cho availability hot-path + invalidation theo booking event.
- [ ] Resilience4j timeout/retry/circuit-breaker cho service calls.
- [ ] Audit log nghiep vu (booking/payment/pricing changes).
- [ ] E2E test matrix + contract test cho event schema.
- [ ] SLI/SLO + alert tuning (5xx rate, consumer lag, DLQ growth).

Definition of Done:
- [ ] He thong degrade graceful khi dependency loi.
- [ ] Co runbook replay/rollback ro rang.

## Cross-sprint best practices (bat buoc)
- [ ] Moi write API phai co idempotency policy ro rang.
- [ ] Moi event phai co schemaVersion + backward compatibility.
- [ ] Moi migration phai co rollback/repair strategy va script verify.
- [ ] Moi sprint phai co test gate trong CI (compile + fast-tests + smoke E2E).
- [ ] Secret khong hardcode; dung env/secret manager profile theo moi truong.

## Notes
- Thuc hien theo thu tu uu tien tu Sprint 1 -> Sprint 5.
- Sprint 4 co the bat dau song song Sprint 3 sau khi Pricing/Reporting APIs on dinh.
- Quy dinh hien tai: tam bo qua Sprint 4 va Sprint 5, se quay lai sau.
