# Frontend Pre-release Review (Booking Funnel)

Ngay cap nhat: 2026-04-23

## 1) Demo script chuan
1. Landing: nhap search va vao Discover.
2. Discover: dung filter/sort de tim card phu hop.
3. Venue detail / Booking grid: chon slot hop le.
4. Checkout: tao booking draft.
5. Payment: initiate payment + callback success.
6. Account: kiem tra booking status sau thanh toan.

## 2) Cac diem can ghi nhan trong buoi review
- User co bi "dung lai de nghi" o buoc nao khong?
- CTA chinh co de thay khong?
- Message loi co de hieu khong?
- Co mat context booking khi doi man hinh/login/reload khong?
- Co sai lech trang thai giua booking va payment khong?

## 3) Muc uu tien fix
- P0: Vo luong booking/payment, khong tao draft duoc, state sai nghiep vu.
- P1: Mat context, CTA mo ho, error message kho hieu, responsive vo layout.
- P2: Polishing visual, copywriting, animation.

## 4) Ket luan release gate
- [ ] Khong con P0
- [ ] P1 da duoc xu ly hoac co workaround ro rang
- [ ] Funnel smoke test pass
- [ ] QA manual checklist da tick day du
