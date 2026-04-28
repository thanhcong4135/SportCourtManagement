import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { BottomNavigation } from "../../components/BottomNavigation";
import { Modal, StatusBadge } from "../../components/ui";
import { useAuth } from "../../context/AuthContext";
import {
  canCancelBooking,
  canDepositBooking,
  canRescheduleBooking,
  getBookingStatusLabel,
  getBookingStatusVariant,
  getPaymentStatusLabel,
  getPaymentStatusVariant,
} from "../../features/booking/bookingPresentation";
import { formatCurrency } from "../../lib/api";
import { toErrorPresentation } from "../../lib/errorPresentation";
import {
  buildOffsetIso,
  cancelBooking,
  getBookingById,
  listCourts,
  listPaymentByBooking,
  listVenues,
  rescheduleBooking,
  type Booking,
  type PaymentTransaction,
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

function mapPaymentTransactionStatus(status: PaymentTransaction["status"]) {
  switch (status) {
    case "SUCCESS":
      return "Thành công";
    case "PENDING":
      return "Chờ xử lý";
    case "FAILED":
      return "Thất bại";
    case "CANCELED":
      return "Đã hủy";
    default:
      return status;
  }
}

export function BookingDetailPage() {
  const { bookingId } = useParams<{ bookingId: string }>();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  const [booking, setBooking] = useState<Booking | null>(null);
  const [payments, setPayments] = useState<PaymentTransaction[]>([]);
  const [courtName, setCourtName] = useState<string>("");
  const [venueName, setVenueName] = useState<string>("");
  const [showCancelModal, setShowCancelModal] = useState(false);
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
        const [row, paymentRows] = await Promise.all([
          getBookingById(currentBookingId),
          listPaymentByBooking(currentBookingId),
        ]);
        setBooking(row);
        setPayments(paymentRows);
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

  useEffect(() => {
    if (!booking) {
      setCourtName("");
      setVenueName("");
      return;
    }
    const selectedCourtId = booking.courtId;

    async function loadCourtVenueMeta() {
      try {
        const venues = await listVenues();
        const venuesWithCourts = await Promise.all(
          venues.map(async (venue) => ({
            venue,
            courts: await listCourts(venue.id),
          })),
        );

        for (const entry of venuesWithCourts) {
          const matchedCourt = entry.courts.find((court) => court.id === selectedCourtId);
          if (matchedCourt) {
            setCourtName(matchedCourt.name);
            setVenueName(entry.venue.name);
            return;
          }
        }
      } catch {
        // Optional metadata for display only.
      }
    }

    void loadCourtVenueMeta();
  }, [booking]);

  const bookingDuration = useMemo(() => {
    if (!booking) {
      return "-";
    }
    const ms = new Date(booking.endTime).getTime() - new Date(booking.startTime).getTime();
    return `${(ms / 3600000).toFixed(1)}h`;
  }, [booking]);

  const sortedPayments = useMemo(
    () => [...payments].sort((left, right) => new Date(right.requestedAt).getTime() - new Date(left.requestedAt).getTime()),
    [payments],
  );

  const latestSuccessPayment = useMemo(
    () => sortedPayments.find((payment) => payment.status === "SUCCESS") ?? null,
    [sortedPayments],
  );

  const canCancel = booking ? canCancelBooking(booking) : false;
  const canDeposit = booking ? canDepositBooking(booking) : false;
  const canReschedule = booking ? canRescheduleBooking(booking) : false;
  const hasAnyAction = canCancel || canDeposit || canReschedule;

  async function doCancel() {
    if (!bookingId || !booking || !canCancelBooking(booking)) {
      return;
    }

    try {
      setBusy(true);
      setError(null);
      setTraceId(null);
      const row = await cancelBooking(bookingId);
      setBooking(row);
      setNotice("Đã hủy đặt lịch.");
      setShowCancelModal(false);
    } catch (err) {
      const uiError = toErrorPresentation(err, "Không hủy được booking");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  async function handleReschedule() {
    if (!bookingId || !booking || !canRescheduleBooking(booking)) {
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

  function handleDownloadReceipt() {
    if (!booking || !latestSuccessPayment) {
      return;
    }
    const receipt = [
      `Receipt booking #${booking.id}`,
      `Court: ${booking.courtId}`,
      `Time: ${booking.startTime} -> ${booking.endTime}`,
      `Total: ${booking.priceTotal}`,
      `Deposit required: ${booking.depositRequired}`,
      `Deposit paid: ${booking.depositPaid}`,
      `Payment id: ${latestSuccessPayment.id}`,
      `Provider: ${latestSuccessPayment.provider}`,
      `Completed at: ${latestSuccessPayment.completedAt ?? "-"}`,
    ].join("\n");

    const blob = new Blob([receipt], { type: "text/plain;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `receipt-${booking.id.slice(0, 8)}.txt`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  function contactSupport() {
    if (!bookingId) {
      return;
    }
    const subject = encodeURIComponent(`Hỗ trợ booking ${bookingId}`);
    const body = encodeURIComponent("Xin hỗ trợ về đơn đặt sân của tôi.");
    window.location.href = `mailto:support@sportcourt.local?subject=${subject}&body=${body}`;
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
          <div className="booking-status-row">
            <StatusBadge
              label={booking ? getBookingStatusLabel(booking.status) : "-"}
              variant={booking ? getBookingStatusVariant(booking.status) : "neutral"}
            />
            <StatusBadge
              label={booking ? getPaymentStatusLabel(booking.paymentStatus) : "-"}
              variant={booking ? getPaymentStatusVariant(booking.paymentStatus) : "neutral"}
            />
          </div>
          <p><strong>Sân:</strong> {courtName || `#${booking?.courtId.slice(0, 8) ?? "-"}`}</p>
          <p><strong>Cụm sân:</strong> {venueName || "-"}</p>
          <p><strong>Khung giờ:</strong> {booking ? `${toLocalDateTime(booking.startTime)} - ${toLocalDateTime(booking.endTime)}` : "-"}</p>
          <p><strong>Tổng giờ:</strong> {bookingDuration}</p>
          <p><strong>Tổng tiền:</strong> {formatCurrency(booking?.priceTotal ?? 0)}</p>
          <p><strong>Tiền cọc yêu cầu:</strong> {formatCurrency(booking?.depositRequired ?? 0)}</p>
          <p><strong>Đã cọc:</strong> {formatCurrency(booking?.depositPaid ?? 0)}</p>
        </article>

        <article className="booking-detail-card">
          <h2>Thao tác</h2>
          {canCancel ? (
            <p className="booking-actions-note">
              Bạn được hủy booking khi đơn ở trạng thái chờ cọc hoặc đã xác nhận, và trước giờ bắt đầu.
            </p>
          ) : (
            <p className="booking-actions-note">
              Booking hiện tại không còn điều kiện hủy theo chính sách.
            </p>
          )}

          {hasAnyAction ? (
            <div className="booking-actions-list">
              <button type="button" className="ghost-cta" onClick={() => navigate(`/payment/${bookingId}`)} disabled={!bookingId || busy}>
                XEM TRẠNG THÁI THANH TOÁN
              </button>

              {canDeposit ? (
                <button type="button" className="booking-cta" onClick={() => navigate(`/payment/${bookingId}`)} disabled={!bookingId || busy}>
                  ĐẶT CỌC
                </button>
              ) : null}

              {canCancel ? (
                <button type="button" className="danger-cta" onClick={() => setShowCancelModal(true)} disabled={busy || !bookingId}>
                  HỦY ĐẶT LỊCH
                </button>
              ) : null}

              {canReschedule ? (
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
              ) : null}
            </div>
          ) : (
            <p className="booking-actions-note">Booking này không còn thao tác khả dụng.</p>
          )}

          <div className="booking-receipt-box">
            <h3>Biên nhận / thanh toán</h3>
            {latestSuccessPayment ? (
              <>
                <p><strong>Giao dịch:</strong> #{latestSuccessPayment.id.slice(0, 8)}</p>
                <p><strong>Trạng thái:</strong> {mapPaymentTransactionStatus(latestSuccessPayment.status)}</p>
                <p><strong>Số tiền:</strong> {formatCurrency(latestSuccessPayment.amount)}</p>
                <p><strong>Thời điểm:</strong> {latestSuccessPayment.completedAt ? toLocalDateTime(latestSuccessPayment.completedAt) : "-"}</p>
                <div className="booking-receipt-actions">
                  <button type="button" className="ghost-cta" onClick={handleDownloadReceipt}>Tải biên nhận</button>
                  <button type="button" className="ghost-cta" onClick={contactSupport}>Liên hệ hỗ trợ</button>
                </div>
              </>
            ) : (
              <p className="muted">Chưa có giao dịch thành công để xuất biên nhận.</p>
            )}
          </div>

          {sortedPayments.length > 0 ? (
            <div className="booking-payment-history">
              <h3>Lịch sử giao dịch</h3>
              <ul className="list-clean compact-list">
                {sortedPayments.map((payment) => (
                  <li key={payment.id}>
                    #{payment.id.slice(0, 8)} · {mapPaymentTransactionStatus(payment.status)} · {formatCurrency(payment.amount)}
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </article>
      </section>

      <BottomNavigation active="account" />

      <Modal
        open={showCancelModal}
        title="Xác nhận hủy booking"
        message="Thao tác này sẽ hủy lịch đặt hiện tại. Bạn có chắc muốn tiếp tục?"
        confirmLabel="Xác nhận hủy"
        cancelLabel="Giữ lại"
        onCancel={() => setShowCancelModal(false)}
        onConfirm={() => { void doCancel(); }}
      />
    </div>
  );
}
