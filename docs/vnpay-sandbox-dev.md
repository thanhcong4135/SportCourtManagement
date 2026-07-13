# VNPAY Sandbox Payment Integration

## Mục đích

Tích hợp VNPAY sandbox để kiểm thử flow thanh toán tiền cọc cho booking.

Flow hiện tại:

1. Frontend gọi `POST /api/payments/vnpay/create-payment`.
2. `payment-service` tạo transaction `PENDING`, ký request bằng HMAC SHA512 và trả `paymentUrl`.
3. Frontend chuyển user sang VNPAY sandbox.
4. Sau khi user thanh toán, VNPAY gọi IPN backend để xác nhận kết quả.
5. Backend xác thực `vnp_TmnCode`, chữ ký, số tiền và trạng thái transaction trước khi cập nhật DB.
6. Khi payment đổi sang `SUCCESS` hoặc `FAILED`, backend ghi outbox event để publish sang `payment.events`; `core-service` dùng event này để đồng bộ trạng thái booking.
7. VNPAY redirect trình duyệt user về return URL. Backend xác thực chữ ký return rồi redirect tiếp sang frontend `/payment-result`.
8. Frontend gọi `GET /api/payments/by-ref/{paymentRef}` để đọc trạng thái chính thức.

## Phân biệt IPN URL và Return URL

| URL | Ai gọi | Mục đích | Có cập nhật trạng thái payment không? |
| --- | --- | --- | --- |
| `VNPAY_IPN_URL` | Server VNPAY | Server-to-server callback xác nhận kết quả | Có |
| `VNPAY_RETURN_URL` | Trình duyệt user sau khi rời VNPAY | Đưa user quay lại hệ thống | Không |
| `VNPAY_FRONTEND_RETURN_URL` | Backend redirect tiếp | Hiển thị trang kết quả React | Không |

IPN là nguồn dữ liệu chính thức. Không cập nhật payment hoặc booking chỉ dựa trên return URL vì user có thể đóng tab, mất mạng hoặc tự sửa query string.

`VNPAY_IPN_URL` không được gửi trong query của `create-payment`. URL này phải được đăng ký phía VNPAY Merchant Portal hoặc gửi VNPAY Support cấu hình cho merchant sandbox.

## Biến môi trường

Không commit credential thật. Đặc biệt, không ghi `VNPAY_HASH_SECRET` vào tài liệu, source code hoặc file compose dùng chung.

```powershell
$env:VNPAY_ENABLED="true"
$env:VNPAY_ENVIRONMENT="sandbox"
$env:VNPAY_PAY_URL="https://sandbox.vnpayment.vn/paymentv2/vpcpay.html"
$env:VNPAY_TMN_CODE="<sandbox-tmn-code>"
$env:VNPAY_HASH_SECRET="<sandbox-hash-secret>"
$env:VNPAY_RETURN_URL="https://<public-domain>/api/payments/vnpay/return"
$env:VNPAY_IPN_URL="https://<public-domain>/api/payments/vnpay/ipn"
$env:VNPAY_FRONTEND_RETURN_URL="http://localhost:5173/payment-result"
```

Các giá trị mở rộng đang có default:

| Biến | Default |
| --- | --- |
| `VNPAY_VERSION` | `2.1.0` |
| `VNPAY_COMMAND` | `pay` |
| `VNPAY_CURR_CODE` | `VND` |
| `VNPAY_LOCALE` | `vn` |
| `VNPAY_ORDER_TYPE` | `other` |
| `VNPAY_EXPIRE_MINUTES` | `15` |

Nếu chạy bằng Docker Compose, set env trước khi recreate container:

```powershell
docker compose -f infra/docker/docker-compose.yml up -d --force-recreate payment-service api-gateway
```

Nếu secret đã từng bị commit hoặc gửi qua kênh không an toàn, yêu cầu VNPAY Support rotate secret rồi cập nhật env local.

## Public callback URL với ngrok

VNPAY không gọi được `localhost`. Expose API gateway port `8080`:

```powershell
ngrok http 8080
```

Ví dụ ngrok cấp domain:

```text
https://example.ngrok-free.app
```

Khi đó:

```powershell
$env:VNPAY_RETURN_URL="https://example.ngrok-free.app/api/payments/vnpay/return"
$env:VNPAY_IPN_URL="https://example.ngrok-free.app/api/payments/vnpay/ipn"
```

Gửi VNPAY Support cấu hình IPN URL:

```text
https://example.ngrok-free.app/api/payments/vnpay/ipn
```

Nếu dùng ngrok free và domain thay đổi, phải cập nhật env, recreate `payment-service` và yêu cầu VNPAY cập nhật IPN URL.

Có thể xem callback thực tế tại:

```text
http://127.0.0.1:4040/inspect/http
```

## API contract

### Tạo payment URL

```http
POST /api/payments/vnpay/create-payment
Authorization: Bearer <jwt>
Content-Type: application/json
```

```json
{
  "bookingId": "<booking-uuid>",
  "customerId": "<customer-uuid>",
  "amount": 150000,
  "customerName": "Nguyen Van A",
  "customerPhone": "0900000000",
  "orderInfo": "Thanh toan dat san",
  "bankCode": "NCB",
  "idempotencyKey": "payment-vnpay-<unique-value>"
}
```

`bookingId` và `amount` là bắt buộc. Các field còn lại là optional. Dùng lại cùng `idempotencyKey` sẽ trả transaction đã tạo trước đó thay vì tạo payment trùng.

Response `201 Created`:

```json
{
  "paymentId": "<payment-uuid>",
  "paymentRef": "VNPAY20260602123000ABCDEF12",
  "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?..."
}
```

### IPN callback

```http
GET /api/payments/vnpay/ipn?<vnpay-query-params>
```

Endpoint public, không yêu cầu JWT. Backend kiểm tra:

- Có đủ `vnp_TxnRef`, `vnp_Amount`, `vnp_ResponseCode`, `vnp_TransactionStatus`, `vnp_TmnCode`, `vnp_SecureHash`.
- `vnp_TmnCode` khớp merchant đang cấu hình.
- Chữ ký HMAC SHA512 hợp lệ.
- `vnp_TxnRef` tồn tại trong DB.
- `vnp_Amount / 100` khớp amount của transaction.
- Chỉ cập nhật transaction đang ở trạng thái `PENDING`.

Response cho VNPAY:

| `RspCode` | `Message` | Ý nghĩa |
| --- | --- | --- |
| `00` | `Confirm Success` | Đã xử lý callback |
| `01` | `Order not found` | Không tìm thấy `paymentRef` |
| `02` | `Order Already Update` | Transaction đã được xử lý trước đó |
| `04` | `Invalid amount` | Số tiền không khớp |
| `97` | `Invalid Signature` | Chữ ký không hợp lệ |
| `99` | `Invalid request` hoặc `Unknown error` | Thiếu param, sai merchant hoặc lỗi ngoài dự kiến |

### Return URL

```http
GET /api/payments/vnpay/return?<vnpay-query-params>
```

Endpoint public, không yêu cầu JWT. Backend kiểm tra chữ ký rồi redirect `302 Found` sang:

```text
http://localhost:5173/payment-result?paymentRef=<ref>&responseCode=<code>&signature=valid|unknown
```

Return URL chỉ phục vụ UX. Nó không cập nhật DB và không publish event.

### Đọc trạng thái payment

```http
GET /api/payments/by-ref/{paymentRef}
Authorization: Bearer <jwt>
```

Frontend `/payment-result` dùng endpoint này để polling trạng thái thật sau khi user quay lại từ VNPAY.

## Security và gateway route

API gateway route `/api/payments/**` sang `payment-service`.

Public endpoint:

- `GET /api/payments/vnpay/ipn`
- `GET /api/payments/vnpay/return`

Endpoint yêu cầu JWT role `CUSTOMER`, `OWNER` hoặc `ADMIN`:

- `POST /api/payments/vnpay/create-payment`

Endpoint đọc payment yêu cầu một trong các role `CUSTOMER`, `OWNER`, `ADMIN`, `STAFF`, `SUPPORT`.

Frontend không ký hash và không được biết `VNPAY_HASH_SECRET`.

## Lưu ý kỹ thuật

- `vnp_Amount` gửi sang VNPAY bằng số tiền VND nhân `100`.
- `vnp_OrderInfo` được normalize bỏ dấu tiếng Việt trước khi ký.
- Khi tạo chữ ký hoặc verify callback, sort param theo key tăng dần.
- Loại `vnp_SecureHash` và `vnp_SecureHashType` khỏi dữ liệu dùng để ký.
- Payment thành công khi cả `vnp_ResponseCode` và `vnp_TransactionStatus` đều bằng `00`.
- Payment callback hợp lệ được ghi vào `raw_callback_data` để phục vụ kiểm tra.
- Sau khi cập nhật payment, outbox scheduler publish event sang Kafka; booking có thể cần một khoảng ngắn để đồng bộ trạng thái.

## Checklist kiểm thử sandbox

1. Chạy gateway, payment-service, core-service, Kafka và các database liên quan.
2. Chạy `ngrok http 8080`.
3. Xác nhận VNPAY Support đã cấu hình đúng IPN URL public hiện tại.
4. Tạo booking và chọn `VNPAY Sandbox` ở màn thanh toán.
5. Thanh toán bằng thông tin thẻ test do VNPAY cung cấp.
6. Xác nhận trình duyệt quay về `/payment-result`.
7. Mở ngrok inspector và kiểm tra request thật tới `/api/payments/vnpay/ipn`.
8. Kỳ vọng IPN trả `{"RspCode":"00","Message":"Confirm Success"}` trong lần xử lý đầu.
9. Kiểm tra payment transaction đổi từ `PENDING` sang `SUCCESS`.
10. Kiểm tra booking được đồng bộ trạng thái payment sau khi `payment.events` được consume.

## Troubleshooting

### VNPAY báo `Sai chữ ký`

- Kiểm tra `VNPAY_TMN_CODE` và `VNPAY_HASH_SECRET` đúng cặp credential sandbox mới nhất.
- Recreate `payment-service` sau khi thay env.
- Không tự encode query thêm lần nữa sau khi backend đã tạo `paymentUrl`.

### IPN không xuất hiện trong ngrok inspector

- Kiểm tra IPN URL đã được đăng ký phía VNPAY.
- Kiểm tra domain ngrok còn hoạt động và đang forward tới port `8080`.
- Không dùng URL frontend hoặc port `8083`; callback public nên đi qua API gateway port `8080`.

### IPN trả `97 Invalid Signature`

- Phân biệt request thật từ VNPAY với request test thủ công thiếu `vnp_SecureHash`.
- Kiểm tra secret runtime trong container đã được cập nhật.
- Kiểm tra callback không bị proxy hoặc middleware sửa query params.

### Trang kết quả thành công nhưng booking vẫn chưa cập nhật

- Kiểm tra IPN đã đến backend và trả `RspCode=00`.
- Kiểm tra payment transaction đã đổi sang `SUCCESS`.
- Kiểm tra outbox event và consumer `payment.events` của `core-service`.
- Return URL hợp lệ không đồng nghĩa booking đã được cập nhật; return chỉ phục vụ redirect UI.

## TODO production

- Dùng secret manager thay vì default credential trong source hoặc compose.
- Dùng domain HTTPS ổn định cho IPN và return URL.
- Đăng ký domain production với VNPAY.
- Bổ sung monitoring cho IPN lỗi chữ ký, amount mismatch và outbox backlog.
- Bổ sung integration test với sample callback params do VNPAY cung cấp.
