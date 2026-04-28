# Frontend Phase 4 - Discover Live Insights

Ngay cap nhat: 2026-04-23

## Muc tieu
- Giam du lieu gia lap o trang Discover.
- Hien thi thong tin "co that" theo khung gio user dang tim:
  - Tinh trang trong/kin cua san.
  - Gia quote theo khung gio (neu da dang nhap va backend co pricing rule).

## Pham vi da lam
- File chinh: `frontend/src/pages/customer/DiscoverPage.tsx`

### 1) Live availability theo court
- Goi `GET /api/core/availability` cho cac card dang hien thi.
- Tinh theo `date/time` trong query string (`/discover?date=...&time=...`), fallback:
  - date: hom nay
  - time: `18:00`
- Badge trang thai:
  - `Con cho` (success)
  - `Da kin` (danger)
  - `Dang cap nhat` (neutral)

### 2) Live quote auth-aware
- Da dang nhap: goi `GET /api/core/pricing/quote`.
- Chua dang nhap: khong goi quote, hien "Dang nhap de xem gia".
- Da dang nhap nhung quote fail: hien "Chua co bang gia".

### 3) Sort/filter theo du lieu live
- Filter gia (`price`) dua tren quote live.
- Sort `PRICE_LOW` dua tren quote live.
- Sort `AVAILABILITY` dua tren availability live.

### 4) Summary theo du lieu live
- "Goi y dat coc" o cuoi trang tinh tu quote dau tien co du lieu.

## Ky thuat
- Co `cancelled` guard trong `useEffect` de tranh race condition khi doi filter nhanh.
- Chi fetch cho `visibleCards` de tranh over-fetch.

## Verify
- `npm run -s typecheck` (PASS)
- `npm run -s build` (PASS)
- `npm run -s phase12:smoke` (PASS)
