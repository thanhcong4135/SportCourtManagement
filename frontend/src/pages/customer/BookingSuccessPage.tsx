import { Link, useParams, useSearchParams } from "react-router-dom";
import { Button } from "../../components/ui";

export function BookingSuccessPage() {
  const { bookingId = "" } = useParams<{ bookingId: string }>();
  const [searchParams] = useSearchParams();
  const mode = searchParams.get("mode") ?? "UNKNOWN";

  return (
    <div className="alobo-screen booking-success-screen">
      <section className="booking-success-card">
        <div className="booking-success-icon">✓</div>
        <h1>Đặt lịch thành công</h1>
        <p>Yêu cầu đặt sân đã được tạo thành công trong hệ thống.</p>

        <div className="booking-success-meta">
          <p><strong>Mã booking:</strong> #{bookingId.slice(0, 8)}</p>
          <p><strong>Hình thức:</strong> {mode === "AT_VENUE" ? "Thanh toán tại sân" : "Thanh toán online"}</p>
          <p><strong>Trạng thái:</strong> Chờ xác nhận thanh toán</p>
        </div>

        <div className="booking-success-actions">
          <Link to="/discover"><Button variant="secondary" fullWidth>Về trang chủ</Button></Link>
          <Link to="/account"><Button variant="primary" fullWidth>Xem lịch đặt của tôi</Button></Link>
        </div>
      </section>
    </div>
  );
}

