import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Button } from "../../components/ui";
import { formatCurrency } from "../../lib/api";
import { toErrorPresentation } from "../../lib/errorPresentation";
import { getPaymentByRef, type PaymentByRefStatus } from "../../lib/coreApi";

function statusTitle(status?: string, returnedSuccess?: boolean) {
  if (returnedSuccess) {
    return "Thanh toán thành công";
  }
  switch (status) {
    case "SUCCESS":
      return "Thanh toán thành công";
    case "FAILED":
      return "Thanh toán thất bại";
    case "CANCELED":
      return "Thanh toán đã hủy";
    case "PENDING":
      return "Đang chờ VNPAY xác nhận";
    default:
      return "Đang kiểm tra thanh toán";
  }
}

function statusMessage(status?: string, responseCode?: string, returnedSuccess?: boolean) {
  if (returnedSuccess) {
    return "VNPAY đã trả kết quả thanh toán thành công. Hệ thống sẽ tiếp tục đồng bộ trạng thái chính thức qua IPN.";
  }
  if (status === "SUCCESS") {
    return "Backend đã nhận IPN hợp lệ từ VNPAY và cập nhật giao dịch thành công.";
  }
  if (status === "FAILED" || status === "CANCELED") {
    return `Giao dịch chưa thành công${responseCode ? `, mã phản hồi ${responseCode}` : ""}. Bạn có thể thử thanh toán lại.`;
  }
  return "Bạn đã quay lại từ VNPAY. Trạng thái chính thức phụ thuộc vào IPN từ VNPAY, hệ thống sẽ tự cập nhật khi nhận callback.";
}

export function PaymentResultPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const paymentRef = searchParams.get("paymentRef") || "";
  const responseCode = searchParams.get("responseCode") || undefined;
  const signature = searchParams.get("signature") || undefined;

  const [payment, setPayment] = useState<PaymentByRefStatus | null>(null);
  const [loading, setLoading] = useState(Boolean(paymentRef));
  const [error, setError] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | null>(null);

  useEffect(() => {
    if (!paymentRef) {
      setLoading(false);
      setError("Thiếu mã giao dịch VNPAY.");
      return;
    }

    let cancelled = false;
    async function load() {
      try {
        setLoading(true);
        setError(null);
        setTraceId(null);
        const result = await getPaymentByRef(paymentRef);
        if (!cancelled) {
          setPayment(result);
        }
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được trạng thái thanh toán");
        if (!cancelled) {
          setError(uiError.message);
          setTraceId(uiError.traceId ?? null);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void load();
    const timer = window.setInterval(() => {
      void load();
    }, 5000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [paymentRef]);

  const returnedSuccess = responseCode === "00" && signature === "valid" && payment?.status !== "FAILED" && payment?.status !== "CANCELED";
  const displayStatus = returnedSuccess ? "SUCCESS" : payment?.status;
  const title = statusTitle(payment?.status, returnedSuccess);

  return (
    <div className="alobo-screen payment-screen">
      <header className="simple-topbar">
        <Link to="/discover" className="back-link">←</Link>
        <h1>Kết quả thanh toán</h1>
        <div className="topbar-spacer" />
      </header>

      <section className="payment-result-card">
        <div className={`payment-result-icon payment-result-icon-${displayStatus?.toLowerCase() || "pending"}`}>
          {displayStatus === "SUCCESS" ? "✓" : displayStatus === "FAILED" ? "!" : "…"}
        </div>
        <h2>{loading ? "Đang kiểm tra thanh toán..." : title}</h2>
        <p>{statusMessage(payment?.status, responseCode, returnedSuccess)}</p>

        {payment ? (
          <div className="payment-result-details">
            <p><strong>Mã giao dịch:</strong> {payment.paymentRef}</p>
            <p><strong>Mã booking:</strong> #{payment.bookingId.slice(0, 8)}</p>
            <p><strong>Số tiền:</strong> {formatCurrency(payment.amount)}</p>
            <p><strong>Nhà cung cấp:</strong> {payment.provider}</p>
            <p><strong>Trạng thái:</strong> {payment.status}</p>
            <p><strong>Mã phản hồi:</strong> {payment.responseCode || responseCode || "-"}</p>
            <p><strong>Chữ ký return:</strong> {signature === "valid" ? "Hợp lệ" : "Chưa xác thực"}</p>
          </div>
        ) : null}

        {error ? <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p> : null}

        <div className="payment-actions-row centered-actions">
          <Button variant="secondary" onClick={() => navigate("/discover")}>Về trang chủ</Button>
          <Button variant="primary" onClick={() => navigate("/account")}>Xem lịch đặt của tôi</Button>
          {payment?.status === "FAILED" ? (
            <Button variant="ghost" onClick={() => navigate(`/payment/${payment.bookingId}`)}>Thử lại thanh toán</Button>
          ) : null}
        </div>
      </section>
    </div>
  );
}
