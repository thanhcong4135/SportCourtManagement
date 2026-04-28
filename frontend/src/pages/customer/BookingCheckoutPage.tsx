import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { trackEvent } from "../../lib/analytics";
import { ApiRequestError, formatCurrency } from "../../lib/api";
import { toErrorPresentation } from "../../lib/errorPresentation";
import {
  type Court,
  type Venue,
  buildOffsetIso,
  checkAvailability,
  createBookingDraft,
  listCourts,
  listVenues,
  quoteBooking,
} from "../../lib/coreApi";

export function BookingCheckoutPage() {
  const navigate = useNavigate();
  const { token, isAuthenticated } = useAuth();
  const [searchParams] = useSearchParams();

  const venueId = searchParams.get("venueId") ?? "";
  const courtId = searchParams.get("courtId") ?? "";
  const date = searchParams.get("date") ?? "";
  const start = searchParams.get("start") ?? "";
  const end = searchParams.get("end") ?? "";

  const [venues, setVenues] = useState<Venue[]>([]);
  const [courts, setCourts] = useState<Court[]>([]);
  const [contactName, setContactName] = useState("");
  const [phone, setPhone] = useState("");
  const [note, setNote] = useState("");
  const [available, setAvailable] = useState<boolean | null>(null);
  const [quote, setQuote] = useState<number | null>(null);
  const [pricingUnavailable, setPricingUnavailable] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | null>(null);

  const selectedVenue = useMemo(() => venues.find((item) => item.id === venueId) ?? null, [venueId, venues]);
  const selectedCourt = useMemo(() => courts.find((item) => item.id === courtId) ?? null, [courtId, courts]);
  const durationHours = useMemo(() => {
    if (!start || !end) {
      return 0;
    }
    const [startHour, startMinute] = start.split(":").map(Number);
    const [endHour, endMinute] = end.split(":").map(Number);
    const startTotal = startHour * 60 + startMinute;
    const endTotal = endHour * 60 + endMinute;
    return Math.max(0, (endTotal - startTotal) / 60);
  }, [end, start]);
  const slotCount = Math.max(0, Math.round(durationHours * 2));
  const estimatedDeposit = quote !== null ? Math.ceil((quote * 0.5) / 1000) * 1000 : 0;

  useEffect(() => {
    if (!date || !start || !end || !courtId) {
      return;
    }

    async function loadQuoteAndAvailability() {
      try {
        setError(null);
        setTraceId(null);
        setPricingUnavailable(false);
        setLoading(true);

        const startIso = buildOffsetIso(date, start);
        const endIso = buildOffsetIso(date, end);

        const [availabilityResult, quoteResult] = await Promise.allSettled([
          checkAvailability(courtId, startIso, endIso),
          quoteBooking(courtId, startIso, endIso),
        ]);

        if (availabilityResult.status === "fulfilled") {
          setAvailable(availabilityResult.value.available);
        } else {
          setAvailable(null);
        }

        if (quoteResult.status === "fulfilled") {
          setQuote(quoteResult.value.totalPrice);
          return;
        }

        setQuote(null);
        const quoteError = quoteResult.reason;
        const uiError = toErrorPresentation(quoteError, "Không lấy được báo giá");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);

        if (quoteError instanceof ApiRequestError && quoteError.code === "PRICING_RULE_MISSING") {
          setPricingUnavailable(true);
        }
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không lấy được báo giá");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      } finally {
        setLoading(false);
      }
    }

    void loadQuoteAndAvailability();
  }, [courtId, date, end, start]);

  useEffect(() => {
    if (!date || !start || !end || !courtId) {
      return;
    }
    trackEvent("funnel_checkout_view", {
      venueId,
      courtId,
      date,
      start,
      end,
    });
  }, [courtId, date, end, start, venueId]);

  useEffect(() => {
    async function loadReferences() {
      if (!venueId) {
        return;
      }
      const [venueRows, courtRows] = await Promise.all([listVenues(), listCourts(venueId)]);
      setVenues(venueRows);
      setCourts(courtRows);
    }
    void loadReferences();
  }, [venueId]);

  async function handleConfirmAndPay() {
    if (!date || !start || !end || !courtId) {
      setError("Thiếu thông tin khung giờ đặt");
      return;
    }
    if (!isAuthenticated || !token?.accessToken) {
      const redirect = encodeURIComponent(window.location.pathname + window.location.search);
      trackEvent("funnel_checkout_redirect_login", {
        venueId,
        courtId,
        date,
        start,
        end,
      });
      navigate(`/auth/login?redirect=${redirect}`);
      return;
    }
    if (available === false) {
      setError("Khung giờ đã có người đặt. Hãy chọn khung giờ khác.");
      return;
    }
    if (quote === null || pricingUnavailable) {
      setError("Khung giờ này chưa có bảng giá. Vui lòng chọn khung giờ khác hoặc liên hệ chủ sân.");
      return;
    }

    try {
      setLoading(true);
      setError(null);
      setTraceId(null);
      const startTime = buildOffsetIso(date, start);
      const endTime = buildOffsetIso(date, end);
      const booking = await createBookingDraft({
        courtId,
        startTime,
        endTime,
        priceTotal: quote,
      });

      trackEvent("funnel_checkout_draft_created", {
        bookingId: booking.id,
        venueId,
        courtId,
        date,
        start,
        end,
        quote,
        depositRequired: booking.depositRequired,
      });
      navigate(`/payment/${booking.id}`);
    } catch (err) {
      const uiError = toErrorPresentation(err, "Tạo booking nháp thất bại");
      trackEvent("funnel_checkout_draft_failed", {
        venueId,
        courtId,
        date,
        start,
        end,
        traceId: uiError.traceId ?? null,
      });
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setLoading(false);
    }
  }

  const confirmDisabled = loading || available !== true || quote === null || pricingUnavailable;

  return (
    <div className="alobo-screen booking-checkout-screen">
      <header className="simple-topbar">
        <Link to={`/booking/grid?venueId=${venueId}&courtId=${courtId}`} className="back-link">←</Link>
        <h1>Đặt lịch ngày trực quan</h1>
        <div className="topbar-spacer" />
      </header>

      <section className="checkout-panels">
        <article className="checkout-card">
          <h2>Thông tin sân</h2>
          <p><strong>Tên CLB:</strong> {selectedVenue?.name ?? "-"}</p>
          <p><strong>Địa chỉ:</strong> {selectedVenue?.address ?? "-"}</p>
          <p><strong>Sân:</strong> {selectedCourt?.name ?? "-"}</p>
        </article>

        <article className="checkout-card">
          <h2>Thông tin lịch đặt</h2>
          <p><strong>Ngày:</strong> {date || "-"}</p>
          <p><strong>Khung giờ:</strong> {start || "-"} - {end || "-"}</p>
          <p><strong>Số slot:</strong> {slotCount} slot (30 phút/slot)</p>
          <p><strong>Tổng thời lượng:</strong> {durationHours > 0 ? `${durationHours.toFixed(1)} giờ` : "-"}</p>
          <p><strong>Tổng tiền:</strong> {quote !== null ? formatCurrency(quote) : "Đang tính..."}</p>
          <p><strong>Cọc tối thiểu:</strong> {quote !== null ? formatCurrency(estimatedDeposit) : "Chưa có bảng giá"}</p>
          <p><strong>Trạng thái:</strong> {available === null ? "Đang kiểm tra" : available ? "Còn trống" : "Đã được đặt"}</p>
        </article>
      </section>

      <section className="checkout-methods-preview">
        <h3>Phương thức thanh toán khả dụng</h3>
        <p className="checkout-method-note">Ưu tiên thanh toán QR để hệ thống xác nhận booking nhanh hơn.</p>
        <div className="checkout-method-grid">
          <article className="checkout-method-item is-active">
            <strong>Chuyển khoản QR</strong>
            <p>Xử lý ngay khi payment-service xác nhận callback.</p>
          </article>
          <article className="checkout-method-item is-disabled">
            <strong>Ví điện tử</strong>
            <p>Sẽ mở trong phase sau.</p>
          </article>
        </div>
      </section>

      <section className="checkout-form">
        <label>
          TÊN CỦA BẠN
          <input value={contactName} onChange={(event) => setContactName(event.target.value)} placeholder="Nhập tên" />
        </label>

        <label>
          SỐ ĐIỆN THOẠI
          <input value={phone} onChange={(event) => setPhone(event.target.value)} placeholder="Nhập số điện thoại" />
        </label>

        <label>
          GHI CHÚ CHO CHỦ SÂN
          <textarea value={note} onChange={(event) => setNote(event.target.value)} placeholder="Nhập ghi chú" />
        </label>
      </section>

      <section className="checkout-trust-signals">
        <span>🔒 Bảo mật giao dịch qua API gateway + JWT</span>
        <span>✅ Booking chỉ xác nhận khi backend nhận payment success event</span>
        <span>↩ Có thể quay lại đổi lịch trước khi thanh toán</span>
      </section>

      <section className="checkout-policy-note">
        <strong>Chính sách cọc:</strong> Cần đặt cọc tối thiểu 50% trước giờ chơi. Nếu quá hạn, booking sẽ tự hủy.
      </section>

      {error && <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p>}
      {loading && <p className="inline-muted">Đang xử lý...</p>}

      <div className="checkout-footer">
        <button
          type="button"
          className="primary-bottom-btn"
          onClick={() => { void handleConfirmAndPay(); }}
          disabled={confirmDisabled}
        >
          XÁC NHẬN & THANH TOÁN
        </button>
      </div>

    </div>
  );
}
