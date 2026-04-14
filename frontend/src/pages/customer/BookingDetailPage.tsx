import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { BottomNavigation } from "../../components/BottomNavigation";
import { useAuth } from "../../context/AuthContext";
import { formatCurrency } from "../../lib/api";
import { toErrorPresentation } from "../../lib/errorPresentation";
import {
  buildOffsetIso,
  cancelBooking,
  getBookingById,
  rescheduleBooking,
  type Booking,
  toLocalDateTime,
} from "../../lib/coreApi";

function extractDateTimeForInput(iso: string) {
  const date = new Date(iso);
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  const hh = String(date.getHours()).padStart(2, "0");
  const mm = String(date.getMinutes()).padStart(2, "0");
  return { date: `${y}-${m}-${d}`, time: `${hh}:${mm}` };
}

export function BookingDetailPage() {
  const { bookingId } = useParams<{ bookingId: string }>();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  const [booking, setBooking] = useState<Booking | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [rescheduleDate, setRescheduleDate] = useState("");
  const [rescheduleStart, setRescheduleStart] = useState("");
  const [rescheduleEnd, setRescheduleEnd] = useState("");

  useEffect(() => {
    if (!bookingId || !isAuthenticated) {
      return;
    }

    const currentBookingId = bookingId;

    async function loadBooking() {
      try {
        setError(null);
        setTraceId(null);
        const row = await getBookingById(currentBookingId);
        setBooking(row);
        const start = extractDateTimeForInput(row.startTime);
        const end = extractDateTimeForInput(row.endTime);
        setRescheduleDate(start.date);
        setRescheduleStart(start.time);
        setRescheduleEnd(end.time);
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được chi tiết booking");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      }
    }

    void loadBooking();
  }, [bookingId, isAuthenticated]);

  const bookingDuration = useMemo(() => {
    if (!booking) {
      return "-";
    }
    const ms = new Date(booking.endTime).getTime() - new Date(booking.startTime).getTime();
    return `${(ms / 3600000).toFixed(1)}h`;
  }, [booking]);

  async function handleCancel() {
    if (!bookingId) {
      return;
    }

    try {
      setBusy(true);
      setError(null);
      setTraceId(null);
      const row = await cancelBooking(bookingId);
      setBooking(row);
      setNotice("Đã hủy đặt lịch.");
    } catch (err) {
      const uiError = toErrorPresentation(err, "Không hủy được booking");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  async function handleReschedule() {
    if (!bookingId || !booking) {
      return;
    }
    if (!rescheduleDate || !rescheduleStart || !rescheduleEnd) {
      setError("Cần nhập đủ ngày và giờ để đổi lịch.");
      return;
    }
    const startIso = buildOffsetIso(rescheduleDate, rescheduleStart);
    const endIso = buildOffsetIso(rescheduleDate, rescheduleEnd);

    try {
      setBusy(true);
      setError(null);
      setTraceId(null);
      const row = await rescheduleBooking(bookingId, {
        startTime: startIso,
        endTime: endIso,
        priceTotal: booking.priceTotal,
      });
      setBooking(row);
      setNotice("Đã cập nhật khung giờ đặt sân.");
    } catch (err) {
      const uiError = toErrorPresentation(err, "Không đổi lịch được");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  if (!isAuthenticated) {
    return (
      <div className="alobo-screen booking-detail-screen">
        <header className="simple-topbar">
          <Link to="/discover" className="back-link">←</Link>
          <h1>Chi tiết đặt lịch</h1>
          <div className="topbar-spacer" />
        </header>
        <p className="inline-muted">Cần đăng nhập để xem chi tiết booking.</p>
      </div>
    );
  }

  return (
    <div className="alobo-screen booking-detail-screen">
      <header className="simple-topbar">
        <Link to="/account" className="back-link">←</Link>
        <h1>Chi tiết đặt lịch</h1>
        <div className="topbar-spacer" />
      </header>

      {error && <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p>}
      {notice && <p className="inline-success">{notice}</p>}

      <section className="booking-detail-layout">
        <article className="booking-detail-card">
          <h2>Thông tin</h2>
          <p><strong>Mã lịch đặt:</strong> #{booking?.id.slice(0, 8) ?? "-"}</p>
          <p><strong>Trạng thái:</strong> {booking?.status ?? "-"}</p>
          <p><strong>Trạng thái thanh toán:</strong> {booking?.paymentStatus ?? "-"}</p>
          <p><strong>Sân:</strong> #{booking?.courtId.slice(0, 8) ?? "-"}</p>
          <p><strong>Khung giờ:</strong> {booking ? `${toLocalDateTime(booking.startTime)} - ${toLocalDateTime(booking.endTime)}` : "-"}</p>
          <p><strong>Tổng giờ:</strong> {bookingDuration}</p>
          <p><strong>Tổng tiền:</strong> {formatCurrency(booking?.priceTotal ?? 0)}</p>
          <p><strong>Tiền cọc yêu cầu:</strong> {formatCurrency(booking?.depositRequired ?? 0)}</p>
          <p><strong>Đã cọc:</strong> {formatCurrency(booking?.depositPaid ?? 0)}</p>
        </article>

        <article className="booking-detail-card">
          <h2>Thao tác</h2>
          <button type="button" className="danger-cta" onClick={() => { void handleCancel(); }} disabled={busy || !bookingId}>
            HỦY ĐẶT LỊCH
          </button>
          <button type="button" className="booking-cta" onClick={() => navigate(`/payment/${bookingId}`)} disabled={!bookingId}>
            ĐẶT CỌC
          </button>

          <div className="reschedule-panel">
            <h3>Đổi khung giờ</h3>
            <label>
              Ngày mới
              <input type="date" value={rescheduleDate} onChange={(event) => setRescheduleDate(event.target.value)} />
            </label>
            <label>
              Giờ bắt đầu
              <input type="time" value={rescheduleStart} step={1800} onChange={(event) => setRescheduleStart(event.target.value)} />
            </label>
            <label>
              Giờ kết thúc
              <input type="time" value={rescheduleEnd} step={1800} onChange={(event) => setRescheduleEnd(event.target.value)} />
            </label>
            <button type="button" className="ghost-cta" onClick={() => { void handleReschedule(); }} disabled={busy || !bookingId}>
              Cập nhật lịch
            </button>
          </div>
        </article>
      </section>

      <BottomNavigation active="account" />
    </div>
  );
}
