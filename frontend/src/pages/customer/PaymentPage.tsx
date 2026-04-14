import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { BottomNavigation } from "../../components/BottomNavigation";
import { useAuth } from "../../context/AuthContext";
import { createIdempotencyKey, formatCurrency } from "../../lib/api";
import { toErrorPresentation } from "../../lib/errorPresentation";
import {
  applyPaymentCallback,
  getBookingById,
  initiateDepositPayment,
  listPaymentByBooking,
  toLocalDateTime,
  type Booking,
  type PaymentTransaction,
} from "../../lib/coreApi";

function renderCountdown(deadlineIso: string) {
  const deadline = new Date(deadlineIso).getTime();
  const now = Date.now();
  const remain = Math.max(0, Math.floor((deadline - now) / 1000));
  const mm = String(Math.floor(remain / 60)).padStart(2, "0");
  const ss = String(remain % 60).padStart(2, "0");
  return `${mm}:${ss}`;
}

function mapPaymentStatusLabel(status: string) {
  switch (status) {
    case "PENDING":
      return "Chờ thanh toán";
    case "SUCCESS":
      return "Đã thanh toán";
    case "FAILED":
      return "Thanh toán lỗi";
    case "CANCELED":
      return "Đã hủy";
    default:
      return status;
  }
}

export function PaymentPage() {
  const { bookingId } = useParams<{ bookingId: string }>();
  const navigate = useNavigate();
  const { isAuthenticated, userId } = useAuth();

  const [booking, setBooking] = useState<Booking | null>(null);
  const [payments, setPayments] = useState<PaymentTransaction[]>([]);
  const [activePaymentId, setActivePaymentId] = useState<string>("");
  const [countdown, setCountdown] = useState("--:--");
  const [error, setError] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const activePayment = useMemo(() => {
    if (activePaymentId) {
      return payments.find((item) => item.id === activePaymentId) ?? null;
    }
    return payments[0] ?? null;
  }, [activePaymentId, payments]);

  const latestSuccessPayment = useMemo(() => {
    return payments.find((payment) => payment.status === "SUCCESS") ?? null;
  }, [payments]);

  const canInitiate = Boolean(
    booking
      && userId
      && booking.paymentStatus !== "DEPOSITED"
      && booking.paymentStatus !== "PAID",
  );

  const loadState = useCallback(async (currentBookingId: string) => {
    const [bookingRow, paymentRows] = await Promise.all([
      getBookingById(currentBookingId),
      listPaymentByBooking(currentBookingId),
    ]);
    setBooking(bookingRow);
    setPayments(paymentRows);
    if (!activePaymentId && paymentRows[0]) {
      setActivePaymentId(paymentRows[0].id);
    }
  }, [activePaymentId]);

  useEffect(() => {
    if (!bookingId || !isAuthenticated) {
      return;
    }

    const currentBookingId = bookingId;

    async function load() {
      try {
        setError(null);
        setTraceId(null);
        await loadState(currentBookingId);
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được thông tin thanh toán");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      }
    }

    void load();
  }, [bookingId, isAuthenticated, loadState]);

  useEffect(() => {
    if (!bookingId || !isAuthenticated) {
      return;
    }

    const currentBookingId = bookingId;
    const poll = window.setInterval(() => {
      void loadState(currentBookingId).catch(() => {
        // Polling error can be ignored temporarily.
      });
    }, 5000);

    return () => window.clearInterval(poll);
  }, [bookingId, isAuthenticated, loadState]);

  const deadlineIso = useMemo(() => {
    if (!booking) {
      return "";
    }
    const start = new Date(booking.startTime).getTime();
    const deadline = new Date(start - 30 * 60 * 1000);
    return deadline.toISOString();
  }, [booking]);

  useEffect(() => {
    if (!deadlineIso) {
      return;
    }
    setCountdown(renderCountdown(deadlineIso));

    const handle = window.setInterval(() => {
      setCountdown(renderCountdown(deadlineIso));
    }, 1000);

    return () => window.clearInterval(handle);
  }, [deadlineIso]);

  async function handleInitiatePayment() {
    if (!booking || !bookingId || !userId) {
      setError("Thiếu thông tin booking hoặc user để tạo giao dịch.");
      setTraceId(null);
      return;
    }

    try {
      setBusy(true);
      setError(null);
      setTraceId(null);
      setNotice(null);
      const created = await initiateDepositPayment({
        bookingId,
        customerId: userId,
        amount: booking.depositRequired,
        currency: "VND",
        idempotencyKey: createIdempotencyKey("payment-init"),
      });
      setNotice("Đã tạo giao dịch đặt cọc. Hãy thanh toán theo QR/checkout URL.");
      await loadState(bookingId);
      setActivePaymentId(created.id);
      if (created.checkoutUrl) {
        window.open(created.checkoutUrl, "_blank", "noopener,noreferrer");
      }
    } catch (err) {
      const uiError = toErrorPresentation(err, "Không tạo được giao dịch thanh toán");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  async function handleSimulateSuccess() {
    if (!activePayment) {
      return;
    }

    try {
      setBusy(true);
      setError(null);
      setTraceId(null);
      setNotice(null);
      await applyPaymentCallback({
        paymentId: activePayment.id,
        providerReference: activePayment.providerReference || activePayment.id,
        success: true,
      });
      await loadState(activePayment.bookingId);
      setNotice("Đã mô phỏng callback thanh toán thành công. Core-service sẽ tự cập nhật booking theo event.");
    } catch (err) {
      const uiError = toErrorPresentation(err, "Không mô phỏng callback được");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  if (!isAuthenticated) {
    return (
      <div className="alobo-screen payment-screen">
        <header className="simple-topbar">
          <Link to="/discover" className="back-link">←</Link>
          <h1>Thanh toán</h1>
          <div className="topbar-spacer" />
        </header>
        <p className="inline-muted">Cần đăng nhập để xem trang thanh toán.</p>
        <Link className="pill-link" to="/auth/login">Đăng nhập</Link>
      </div>
    );
  }

  return (
    <div className="alobo-screen payment-screen">
      <header className="simple-topbar">
        <Link to="/account" className="back-link">←</Link>
        <h1>Thanh toán</h1>
        <div className="topbar-spacer" />
      </header>

      <section className="payment-grid">
        <div className="payment-left">
          <article className="payment-method-card">
            <h3>1. MOMO</h3>
            <p>Tên tài khoản: <strong>SPORTCOURT DEMO</strong></p>
            <p>Số điện thoại: <strong>0900000000</strong></p>
            <div className="qr-demo" aria-hidden>QR</div>
          </article>

          <article className="payment-method-card">
            <h3>2. Tài khoản ngân hàng</h3>
            <p>Tên tài khoản: <strong>SPORTCOURT COMPANY</strong></p>
            <p>Số tài khoản: <strong>123456789</strong></p>
            <p>Ngân hàng: <strong>Vietcombank</strong></p>
            <div className="qr-demo" aria-hidden>QR</div>
          </article>

          <p className="payment-warning">
            Vui lòng chuyển khoản <strong>{formatCurrency(booking?.depositRequired ?? 0)}</strong> để đặt cọc.
          </p>

          <div className="upload-placeholder-row">
            <div className="upload-placeholder">Tải ảnh thanh toán</div>
            <div className="upload-placeholder">Tải minh chứng ưu đãi (nếu có)</div>
          </div>
        </div>

        <aside className="payment-right">
          <h3>Thông tin lịch đặt</h3>
          <p><strong>Mã booking:</strong> #{booking?.id.slice(0, 8)}</p>
          <p><strong>Thời gian:</strong> {booking ? `${toLocalDateTime(booking.startTime)} - ${toLocalDateTime(booking.endTime)}` : "-"}</p>
          <p><strong>Tổng đơn:</strong> {formatCurrency(booking?.priceTotal ?? 0)}</p>
          <p><strong>Cần thanh toán:</strong> {formatCurrency(booking?.depositRequired ?? 0)}</p>
          <p><strong>Trạng thái booking:</strong> {booking?.status ?? "-"} / {booking?.paymentStatus ?? "-"}</p>
          <p><strong>Giữ chỗ đến:</strong> {countdown}</p>

          <button type="button" className="booking-cta" onClick={() => { void handleInitiatePayment(); }} disabled={busy || !canInitiate}>
            TẠO GIAO DỊCH ĐẶT CỌC
          </button>

          <button type="button" className="ghost-cta" onClick={() => { void handleSimulateSuccess(); }} disabled={busy || !activePayment || activePayment.status !== "PENDING"}>
            MÔ PHỎNG CALLBACK THÀNH CÔNG
          </button>

          <button type="button" className="ghost-cta" onClick={() => navigate(`/account/bookings/${bookingId}`)}>
            Xem chi tiết đơn
          </button>

          {activePayment && (
            <div className="payment-active-box">
              <p><strong>Giao dịch hiện tại:</strong> #{activePayment.id.slice(0, 8)}</p>
              <p><strong>Trạng thái:</strong> {mapPaymentStatusLabel(activePayment.status)}</p>
              {activePayment.checkoutUrl && (
                <a href={activePayment.checkoutUrl} target="_blank" rel="noreferrer" className="muted">
                  Mở checkout URL
                </a>
              )}
            </div>
          )}

          {!!payments.length && (
            <div className="payment-list-box">
              <p><strong>Lịch sử giao dịch</strong></p>
              <ul className="list-clean compact-list">
                {payments.map((payment) => (
                  <li key={payment.id}>
                    #{payment.id.slice(0, 8)} · {mapPaymentStatusLabel(payment.status)} · {formatCurrency(payment.amount)}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {latestSuccessPayment && (
            <p className="inline-success">Đã có giao dịch thành công. Booking sẽ được xác nhận sau khi event được xử lý.</p>
          )}
        </aside>
      </section>

      {error && <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p>}
      {notice && <p className="inline-success">{notice}</p>}

      <BottomNavigation active="account" />
    </div>
  );
}
