# Backend Step 1 - Empty Services Implemented

Implemented skeleton services:

- `notification-service` (`http://localhost:8084`)
- `reporting-service` (`http://localhost:8085`)
- `chatbot-service` (`http://localhost:8086`)

## Notification service

- `POST /api/notifications/send`
- `GET /api/notifications/{notificationId}`

## Reporting service

- `GET /api/reports/bookings/daily?date=YYYY-MM-DD`

## Chatbot service

- `POST /api/chatbot/messages`

## Notes

- All three services include:
  - Actuator health/info/metrics
  - OpenAPI endpoint + Swagger UI
  - Standardized error contract (`code`, `message`, `details`, `traceId`, `timestamp`, `status`, `path`, `error`)
  - `X-Trace-Id` propagation/generation filter
- `api-gateway` routes were added for `/api/notifications/**`, `/api/reports/**`, `/api/chatbot/**`.
