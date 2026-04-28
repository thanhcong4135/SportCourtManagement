# SportCourt Frontend (BlueFlow)

Frontend duoc xay dung dua tren flow tham chieu trong `Alobo_UI_Flow.pdf` (khach hang + van hanh), da dong bo tông xanh duong voi backend hien tai.

## Chuc nang da co

- **Khach hang portal** (`/customer`)
  - Chon venue/court
  - Check availability + quote gia
  - Tao booking draft
  - Dat coc + confirm booking
  - Tao add-on order theo booking
- **Operations portal** (`/ops`)
  - Tao venue, court, product
  - Xem report occupancy / revenue / best-hours theo date range + venue
- **Auth popup**
  - Register/Login qua `auth-service`
  - Luu JWT/refresh token local

## Tech stack

- React + TypeScript + Vite
- React Router
- Goi API qua `api-gateway`

## Chay local

```bash
cd frontend
npm install
npm run dev
```

Mac dinh frontend goi API vao `http://localhost:8080`.

Neu can doi:

```bash
# .env.local
VITE_API_BASE_URL=http://localhost:8080
```

## Luu y

- Cac thao tac tao/ghi du lieu (`venues/courts/products/bookings/orders`) can role phu hop (`OWNER/ADMIN/CUSTOMER`) theo backend security.
- Frontend nay la MVP theo Sprint 1-3, chua gom Sprint 4-5.
