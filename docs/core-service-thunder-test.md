# Core Service - Thunder Client Test Flow

## 0) Prerequisites
- Core service running at `http://localhost:8081`
- MySQL + Kafka running
- Header for POST requests: `Content-Type: application/json`
- For booking APIs, add `Authorization: Bearer <JWT>`
  - `sub` must be UUID
  - `roles` claim must include `CUSTOMER` (or `ADMIN`/`OWNER`)

## 1) Create venue
- `POST /api/core/venues`
```json
{
  "name": "SportCourt Center Q1",
  "address": "123 Nguyen Hue, Q1, HCM"
}
```
- Save `venueId` from response.

## 2) Create courts
- `POST /api/core/courts`
```json
{
  "venueId": "VENUE_ID",
  "name": "San 1",
  "sportType": "BADMINTON"
}
```

- `POST /api/core/courts`
```json
{
  "venueId": "VENUE_ID",
  "name": "San 3",
  "sportType": "BADMINTON"
}
```
- Save `courtId1`, `courtId2` from response.

## 3) Availability before booking
- `GET /api/core/availability?courtId=COURT_ID1&start=2026-03-05T08:00:00%2B07:00&end=2026-03-05T10:00:00%2B07:00`
- Expected: `available = true`

## 4) Single booking flow
- `POST /api/core/bookings/draft`
```json
{
  "courtId": "COURT_ID1",
  "customerId": "11111111-1111-1111-1111-111111111111",
  "startTime": "2026-03-05T08:00:00+07:00",
  "endTime": "2026-03-05T10:00:00+07:00",
  "priceTotal": 400000
}
```
- Save `bookingId1`.

- `POST /api/core/bookings/BOOKING_ID1/deposit`
```json
{
  "amount": 200000
}
```

- `POST /api/core/bookings/BOOKING_ID1/confirm`

- Check again:
  - `GET /api/core/availability?courtId=COURT_ID1&start=2026-03-05T08:00:00%2B07:00&end=2026-03-05T10:00:00%2B07:00`
  - Expected: `available = false`

## 5) Batch booking flow
- `POST /api/core/bookings/draft/batch`
```json
{
  "customerId": "11111111-1111-1111-1111-111111111111",
  "items": [
    {
      "courtId": "COURT_ID1",
      "startTime": "2026-03-06T08:00:00+07:00",
      "endTime": "2026-03-06T10:00:00+07:00",
      "priceTotal": 400000
    },
    {
      "courtId": "COURT_ID2",
      "startTime": "2026-03-06T19:00:00+07:00",
      "endTime": "2026-03-06T21:00:00+07:00",
      "priceTotal": 500000
    }
  ]
}
```
- Save `bookingId2`, `bookingId3`.

- `POST /api/core/bookings/deposit/batch`
```json
{
  "items": [
    {
      "bookingId": "BOOKING_ID2",
      "amount": 200000
    },
    {
      "bookingId": "BOOKING_ID3",
      "amount": 250000
    }
  ]
}
```

- `POST /api/core/bookings/confirm/batch`
```json
{
  "bookingIds": [
    "BOOKING_ID2",
    "BOOKING_ID3"
  ]
}
```

- Verify availability:
  - `GET /api/core/availability?courtId=COURT_ID1&start=2026-03-06T08:00:00%2B07:00&end=2026-03-06T10:00:00%2B07:00` => `false`
  - `GET /api/core/availability?courtId=COURT_ID2&start=2026-03-06T19:00:00%2B07:00&end=2026-03-06T21:00:00%2B07:00` => `false`

## 6) Negative tests
- Deposit < 50%:
  - `POST /api/core/bookings/BOOKING_ID/deposit` body `{ "amount": 100000 }`
  - Expected: `400`

- Confirm when not deposited:
  - `POST /api/core/bookings/BOOKING_ID/confirm`
  - Expected: `400`

- Overlap same court/time:
  - Create another draft same court + overlapping slot
  - Expected: `409`

- Invalid slot alignment:
  - `startTime` like `08:15`
  - Expected: `400`

## 7) Timeout scheduler check (optional)
- Create DRAFT near start time and do not deposit.
- Wait ~1-2 minutes.
- Expected: booking status changes to `FAILED_TIMEOUT`.

## 8) Booking query APIs
- Get booking by id:
  - `GET /api/core/bookings/BOOKING_ID`
  - Expected: return booking detail with correct `status`, `paymentStatus`, `startTime`, `endTime`.

- List bookings with filters:
  - `GET /api/core/bookings?customerId=CUSTOMER_ID&status=CONFIRMED&page=0&size=20&sort=createdAt,desc`
  - `GET /api/core/bookings?courtId=COURT_ID&from=2026-03-06T00:00:00%2B07:00&to=2026-03-07T00:00:00%2B07:00`
  - Expected: return paged payload with `items`, `page`, `size`, `totalElements`, `totalPages`, `hasNext`.
