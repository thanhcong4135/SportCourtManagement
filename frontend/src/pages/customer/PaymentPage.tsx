import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { PaymentMethodSelector } from "../../components/booking/PaymentMethodSelector";
import { Button } from "../../components/ui";
import { useAuth } from "../../context/AuthContext";
import { trackEvent } from "../../lib/analytics";
import { createIdempotencyKey, formatCurrency } from "../../lib/api";
import { toErrorPresentation } from "../../lib/errorPresentation";
import {
  applyPaymentCallback,
  createVnpayPayment,
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
  return `${String(Math.floor(remain / 60)).padStart(2, "0")}:${String(remain % 60).padStart(2, "0")}`;
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

type PaymentMethodKey = "BANK_QR" | "MOMO_QR" | "VNPAY";

const paymentMethods = [
  { key: "BANK_QR", label: "Chuyển khoản QR ngân hàng", description: "Tạo giao dịch cọc nội bộ qua payment-service.", available: true },
  { key: "MOMO_QR", label: "Ví điện tử QR", description: "Sẵn sàng mở rộng ở phase tiếp theo.", available: true },
  { key: "VNPAY", label: "VNPAY Sandbox", description: "Chuyển sang cổng thanh toán VNPAY sandbox.", available: true },
];

export function PaymentPage() {
  const { bookingId } = useParams<{ bookingId: string }>();
  const navigate = useNavigate();
  const { isAuthenticated, token, userId } = useAuth();

  const [booking, setBooking] = useState<Booking | null>(null);
  const [payments, setPayments] = useState<PaymentTransaction[]>([]);
  const [activePaymentId, setActivePaymentId] = useState("");
  const [selectedMethod, setSelectedMethod] = useState<PaymentMethodKey>("BANK_QR");
  const [countdown, setCountdown] = useState("--:--");
  const [error, setError] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const statusMarkerRef = useRef("");

  const sortedPayments = useMemo(
    () => [...payments].sort((left, right) => new Date(right.requestedAt).getTime() - new Date(left.requestedAt).getTime()),
    [payments],
  );

  const activePayment = useMemo(() => {
    if (activePaymentId) {
      return sortedPayments.find((item) => item.id === activePaymentId) ?? null;
    }
    return sortedPayments[0] ?? null;
  }, [activePaymentId, sortedPayments]);

  const latestSuccessPayment = useMemo(
    () => sortedPayments.find((payment) => payment.status === "SUCCESS") ?? null,
    [sortedPayments],
  );
  const pendingVnpayPayment = useMemo(
    () => sortedPayments.find((payment) => payment.status === "PENDING" && payment.provider === "VNPAY" && payment.checkoutUrl) ?? null,
    [sortedPayments],
  );

  const canInitiate = Boolean(
    booking && userId && booking.paymentStatus !== "DEPOSITED" && booking.paymentStatus !== "PAID",
  );
  const isPendingConfirmation = Boolean(
    booking && activePayment && activePayment.status === "PENDING" && booking.paymentStatus === "UNPAID",
  );
  const isPaymentSuccess = Boolean(
    booking && (booking.paymentStatus === "DEPOSITED" || booking.paymentStatus === "PAID" || latestSuccessPayment),
  );
  const paymentActionDisabled = Boolean(
    busy || !canInitiate || (selectedMethod !== "VNPAY" && isPendingConfirmation),
  );

  const loadState = useCallback(async (id: string) => {
    const [bookingRow, paymentRows] = await Promise.all([getBookingById(id), listPaymentByBooking(id)]);
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

    async function load() {
      try {
        setError(null);
        setTraceId(null);
        await loadState(bookingId!);
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được thông tin thanh toán");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      }
    }
    void load();
  }, [bookingId, isAuthenticated, loadState]);

  useEffect(() => {
    if (!booking || !bookingId) {
      return;
    }
    const marker = `${booking.status}|${booking.paymentStatus}`;
    if (marker !== statusMarkerRef.current) {
      statusMarkerRef.current = marker;
      trackEvent("payment_booking_status_changed", { bookingId, marker });
    }
  }, [booking, bookingId]);

  useEffect(() => {
    if (!bookingId || !isAuthenticated) {
      return;
    }
    const timer = window.setInterval(() => {
      void loadState(bookingId!).catch(() => {});
    }, 5000);
    return () => window.clearInterval(timer);
  }, [bookingId, isAuthenticated, loadState]);

  useEffect(() => {
    if (!booking) {
      return;
    }
    const start = new Date(booking.startTime).getTime();
    const deadline = new Date(start - 30 * 60 * 1000).toISOString();
    setCountdown(renderCountdown(deadline));
    const timer = window.setInterval(() => setCountdown(renderCountdown(deadline)), 1000);
    return () => window.clearInterval(timer);
  }, [booking]);

  async function handleInitiatePayment() {
    if (!booking || !bookingId || !userId) {
      setError("Thiếu thông tin booking hoặc user.");
      return;
    }

    try {
      setBusy(true);
      setError(null);
      setTraceId(null);
      setNotice(null);

      if (selectedMethod === "VNPAY") {
        if (pendingVnpayPayment?.checkoutUrl) {
          trackEvent("payment_vnpay_redirect_existing", { bookingId, paymentRef: pendingVnpayPayment.paymentRef });
          window.location.href = pendingVnpayPayment.checkoutUrl;
          return;
        }

        const created = await createVnpayPayment({
          bookingId,
          customerId: userId,
          amount: booking.depositRequired,
          customerName: token?.email || "SportCourt customer",
          orderInfo: `Thanh toan dat san ${bookingId}`,
          bankCode: "NCB",
          idempotencyKey: createIdempotencyKey("payment-vnpay"),
        });
        trackEvent("payment_vnpay_redirect", { bookingId, paymentRef: created.paymentRef });
        window.location.href = created.paymentUrl;
        return;
      }

      const created = await initiateDepositPayment({
        bookingId,
        customerId: userId,
        amount: booking.depositRequired,
        currency: "VND",
        idempotencyKey: createIdempotencyKey("payment-init"),
      });
      await loadState(bookingId);
      setActivePaymentId(created.id);
      setNotice(`Đã tạo giao dịch qua ${selectedMethod === "BANK_QR" ? "QR ngân hàng" : "ví điện tử"}.`);
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
      setNotice("Đã mô phỏng callback thành công.");
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
        <p className="inline-muted">Cần đăng nhập để thanh toán booking.</p>
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
          <section className="checkout-methods-preview">
            <h3>Chọn phương thức thanh toán</h3>
            <PaymentMethodSelector
              options={paymentMethods}
              value={selectedMethod}
              onChange={(value) => setSelectedMethod(value as PaymentMethodKey)}
            />
          </section>

          <article className="payment-method-card">
            <h3>{selectedMethod === "VNPAY" ? "Thanh toán qua VNPAY Sandbox" : "Thanh toán qua QR"}</h3>
            {selectedMethod === "VNPAY" ? (
              <>
                <p>Hệ thống sẽ chuyển bạn sang cổng VNPAY sandbox để hoàn tất thanh toán.</p>
                <p>Trạng thái chính thức chỉ được cập nhật sau khi backend nhận IPN hợp lệ từ VNPAY.</p>
              </>
            ) : (
              <>
                <p>Tên tài khoản: <strong>SPORTCOURT SYSTEM</strong></p>
                <p>Số tài khoản: <strong>123456789</strong></p>
                <div className="qr-demo" aria-hidden>QR</div>
              </>
            )}
          </article>

          <p className="payment-warning">
            Cần thanh toán tối thiểu <strong>{formatCurrency(booking?.depositRequired ?? 0)}</strong> để giữ chỗ.
          </p>

          <div className="payment-actions-row">
            <Button variant="primary" onClick={() => { void handleInitiatePayment(); }} disabled={paymentActionDisabled}>
              {selectedMethod === "VNPAY"
                ? pendingVnpayPayment ? "TIẾP TỤC THANH TOÁN VNPAY" : "THANH TOÁN VNPAY SANDBOX"
                : "TẠO GIAO DỊCH ĐẶT CỌC"}
            </Button>
            {selectedMethod !== "VNPAY" ? (
              <Button variant="ghost" onClick={() => { void handleSimulateSuccess(); }} disabled={busy || !activePayment || activePayment.status !== "PENDING"}>
                MÔ PHỎNG CALLBACK THÀNH CÔNG
              </Button>
            ) : null}
          </div>
        </div>

        <aside className="payment-right">
          <h3>Thông tin lịch đặt</h3>
          <div className="payment-summary-highlight">
            <span>Giữ chỗ đến</span>
            <strong>{countdown}</strong>
          </div>
          <p><strong>Mã booking:</strong> #{booking?.id.slice(0, 8)}</p>
          <p><strong>Khung giờ:</strong> {booking ? `${toLocalDateTime(booking.startTime)} - ${toLocalDateTime(booking.endTime)}` : "-"}</p>
          <p><strong>Tổng đơn:</strong> {formatCurrency(booking?.priceTotal ?? 0)}</p>
          <p><strong>Cần thanh toán:</strong> {formatCurrency(booking?.depositRequired ?? 0)}</p>
          <p><strong>Trạng thái:</strong> {booking?.status ?? "-"} / {booking?.paymentStatus ?? "-"}</p>

          {isPendingConfirmation ? (
            <p className="inline-muted">Đang chờ xác nhận callback... hệ thống tự đồng bộ mỗi 5 giây.</p>
          ) : null}

          {isPaymentSuccess ? (
            <p className="inline-success">Thanh toán đã thành công. Bạn có thể xem lịch đã đặt.</p>
          ) : null}

          <div className="payment-actions-row">
            <Button variant="secondary" onClick={() => navigate(`/account/bookings/${bookingId}`)}>
              Xem chi tiết đơn
            </Button>
            {isPaymentSuccess ? (
              <Button variant="primary" onClick={() => navigate(`/booking/success/${bookingId}`)}>
                Hoàn tất
              </Button>
            ) : null}
          </div>

          {activePayment ? (
            <div className="payment-active-box">
              <p><strong>Giao dịch hiện tại:</strong> #{activePayment.id.slice(0, 8)}</p>
              <p><strong>Trạng thái:</strong> {mapPaymentStatusLabel(activePayment.status)}</p>
              {activePayment.paymentRef ? <p><strong>Payment ref:</strong> {activePayment.paymentRef}</p> : null}
            </div>
          ) : null}

          {sortedPayments.length ? (
            <div className="payment-list-box">
              <p><strong>Lịch sử giao dịch</strong></p>
              <ul className="list-clean compact-list">
                {sortedPayments.map((payment) => (
                  <li key={payment.id}>
                    #{payment.id.slice(0, 8)} · {mapPaymentStatusLabel(payment.status)} · {formatCurrency(payment.amount)}
                  </li>
                ))}
              </ul>
            </div>
          ) : null}
        </aside>
      </section>

      {error && <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p>}
      {notice && <p className="inline-success">{notice}</p>}
    </div>
  );
}
