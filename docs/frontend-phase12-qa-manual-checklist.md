# Frontend Phase 12 - QA Manual Checklist

Ngay cap nhat: 2026-04-23

## 1) Muc tieu
- Co checklist QA tay de chay nhanh truoc release.
- Bao phu desktop + mobile, va day du loading/empty/error/success.

## 2) Ma tran man hinh can test
- `/` (Landing)
- `/discover`
- `/venues/:venueId`
- `/booking/grid`
- `/booking/form`
- `/payment/:bookingId`
- `/account`
- `/account/bookings/:bookingId`

## 3) Desktop checklist
- [ ] Landing submit search dieu huong dung sang `/discover` va giu query (`q/sport/date/time`).
- [ ] Discover hien danh sach card, filter/sort hoat dong, clear all hoat dong.
- [ ] Discover card CTA mo dung venue detail.
- [ ] Venue detail load du court, legend mau dung, chon range slot duoc.
- [ ] Booking grid chon 2 diem tao range dung, khong cho chon slot booked/held.
- [ ] Checkout hien summary dung (venue/court/ngay/gio/tong tien/coc toi thieu).
- [ ] Payment hien trang thai pending/success/fail ro rang, polling cap nhat duoc.
- [ ] Account group booking dung tab (cho thanh toan/sap toi/hoan thanh/da huy).

## 4) Mobile checklist
- [ ] Sticky action de thay (booking summary/CTA) trong flow dat lich.
- [ ] Timeline slot cuon ngang duoc, cot ten san van de doc.
- [ ] Input va button co touch-target de bam.
- [ ] Bottom nav chi hien o man dieu huong chinh, an o flow booking/payment.

## 5) State checklist
- [ ] Loading: skeleton/loading text hien dung cho Discover/Booking/Payment.
- [ ] Empty: co empty-state ro rang khi khong co ket qua.
- [ ] Error: message than thien, co traceId neu backend tra ve.
- [ ] Success: sau callback thanh toan, booking status cap nhat dung.

## 6) Context retention checklist
- [ ] Giu filter query khi user cuon/tai lai Discover.
- [ ] Sau login, redirect ve dung man dang thao tac (neu co `redirect`).
- [ ] Booking context (venue/court/date/start/end) khong mat khi di qua checkout/payment.

## 7) Exit criteria phase 12
- [ ] `npm run -s typecheck` pass
- [ ] `npm run -s build` pass
- [ ] `npm run -s phase12:smoke` pass
- [ ] Checklist manual tren duoc tick khong con blocker P0/P1
