# Frontend Domain Objects va sample payload

Ngay cap nhat: 2026-04-15

## Domain objects can render o frontend
- VenueSummary
- CourtSummary
- Timeslot
- BookingDraft
- PaymentTransactionView
- UserProfileView
- BookingHistoryItem

## TypeScript source of truth
- `frontend/src/types/domain.ts`

## Sample payload
- `docs/frontend-domain-samples.json`

## Ghi chu contract
- Neu backend thieu field can thiet cho UI (minPrice, availableSlotsCount, draftExpiryAt), mo issue contract som.
- Khong bind truc tiep raw DTO vao UI component; su dung mapper khi can.

