# Frontend Phase 11 - API Contract & Mapper Alignment

Ngày cập nhật: 2026-04-17

## 1) Mục tiêu phase 11
- Tách DTO API khỏi model UI để frontend ít gãy khi backend đổi payload.
- Chuẩn hóa cách hiển thị lỗi booking/payment theo `docs/api-error-contract.md`.
- Chốt danh sách field còn thiếu cần phối hợp backend.

## 2) DTO -> UI mapper đã áp dụng

Frontend đã thêm lớp mapper tại:
- `frontend/src/lib/coreApiMapper.ts`

Các API trong `frontend/src/lib/coreApi.ts` giờ nhận dữ liệu `unknown` từ backend rồi map về UI model trước khi trả ra page/hook:
- Venue, Court
- Booking, BookingPage
- PricingQuote, PricingRule
- PaymentTransaction
- Batch booking response

Điều này đảm bảo:
- Parse số an toàn (hỗ trợ trường hợp backend trả decimal dạng string).
- Có fallback hợp lệ cho field thiếu.
- Không bind trực tiếp page vào raw JSON.

## 3) Error contract mapping cho user-facing message

Đã chuẩn hóa message hiển thị qua:
- `frontend/src/lib/errorMessageCatalog.ts`
- `frontend/src/lib/errorPresentation.ts`

Đã cover các case booking/payment quan trọng:
- Unauthorized / Forbidden
- Validation error
- Slot không còn trống
- Thiếu pricing rule
- Draft hết hạn
- Payment fail

Ngoài ra, `details` dạng mảng validation (`[{field,message}]`) đã được map thành `fieldErrors` để form hiển thị đúng field.

## 4) Gap contract cần phối hợp backend (task mở)

### 4.1 Venue listing
Hiện backend `GET /api/core/venues` chưa có đủ signal cho quyết định nhanh trên listing:
- `imageUrl`
- `sportTypes` tổng hợp theo venue
- `minPrice`
- `availableSlotsCount` (theo ngày/khung giờ filter)

### 4.2 Availability grid
Hiện frontend phải tự suy từ bookings. Nên có endpoint trả trực tiếp slot-level:
- `courtId`
- `startTime`, `endTime`
- `status` (AVAILABLE/HELD/BOOKED/MAINTENANCE)
- `price`

### 4.3 Booking draft
Đề xuất payload chuẩn cho `POST /api/core/bookings/draft` response:
- `subtotal`
- `depositRequired`
- `draftExpiryAt`
- `heldSlots[]`

## 5) Đề xuất bước kế tiếp (phase 12)
- Viết test cho funnel chính (listing -> slot -> draft -> checkout -> payment state).
- Bổ sung analytics event cơ bản cho booking funnel.
