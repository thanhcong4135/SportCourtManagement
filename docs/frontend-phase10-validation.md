# Frontend Phase 10 Validation Matrix

Ngay cap nhat: 2026-04-16

## Muc tieu phase 10
- Responsive tot tren mobile/tablet/desktop.
- Hieu nang on dinh voi du lieu listing lon.
- UX "dung that": thao tac dat lich va thanh toan khong bi ngat quang.

## 1) Ma tran responsive can test

| Nhom | Viewport | Pages can test |
|---|---|---|
| Mobile nho | 360x800 | `/discover`, `/booking/grid`, `/booking/form`, `/payment/:bookingId`, `/account` |
| Tablet | 768x1024 | `/discover`, `/booking/grid`, `/account/bookings/:bookingId` |
| Desktop | 1366x768 | Toan bo luong customer + ops pages |

### Tieu chi pass responsive
- Khong bi vo layout, text khong de len nhau.
- CTA chinh luon trong viewport hoac de thay.
- Nut/tap-target toi thieu 44px.
- Bottom nav sat day man hinh, khong de khoang trong.
- Timeline booking cuon ngang duoc, cot ten san sticky o ben trai.

## 2) Ma tran hieu nang can test

### Build-time budget
Chay:

```bash
cd frontend
npm run phase10:verify
```

Script se check:
- Entry JS <= 260KB
- Tong JS <= 650KB
- Tong CSS <= 40KB

### Runtime UX
- Discover page load danh sach ban dau nhanh (render subset + "Xem them").
- Chuyen route khong can tai tat ca bundle ngay lan dau (lazy route chunking).
- Khi doi filter/search, UI khong giat khi danh sach lon.

## 3) Ma tran kha nang dung that (real-usage)

### Discover
- Tim kiem + filter + sort + clear filter.
- Nut "Xem them" hoat dong dung so luong con lai.

### Booking Grid
- Chon slot theo 2 diem (start/end) tren cung 1 hang san.
- Khong cho chon o da dat.
- Chuyen ngay/cum san reset selection dung.

### Checkout + Payment
- Summary slot + tong tien + coc toi thieu hien dung.
- Payment state ro: pending/success/fail.
- Booking detail co action theo trang thai (dat coc/huy/doi lich).

### Account
- Tab grouping dung: cho thanh toan, sap toi, hoan thanh, da huy.
- Danh sach booking + status badge + CTA theo context.

## 4) Regression nhanh truoc merge
- `npm run typecheck`
- `npm run build`
- `npm run check:bundle`
- Kiem tra tay 5 man hinh:
  - `/discover`
  - `/booking/grid`
  - `/booking/form`
  - `/payment/:bookingId`
  - `/account`

Neu cac buoc tren pass thi co the danh dau phase 10 "done" cho nhanh release.
