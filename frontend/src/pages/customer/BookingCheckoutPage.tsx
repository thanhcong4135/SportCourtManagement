import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { PaymentMethodSelector } from "../../components/booking/PaymentMethodSelector";
import { Button } from "../../components/ui";
import { useAuth } from "../../context/AuthContext";
import { trackEvent } from "../../lib/analytics";
import { ApiRequestError, createIdempotencyKey, formatCurrency } from "../../lib/api";
import { toErrorPresentation } from "../../lib/errorPresentation";
import {
  buildOffsetIso,
  checkAvailability,
  createBookingDraft,
  listCourts,
  listVenues,
  quoteBooking,
  type Court,
  type Venue,
} from "../../lib/coreApi";

type PaymentMethodKey = "AT_VENUE" | "BANK_QR" | "DEPOSIT_ONLINE";

type CustomerFormErrors = {
  contactName?: string;
  phone?: string;
};

const paymentOptions = [
  {
    key: "AT_VENUE",
    label: "Thanh toán tại sân",
    description: "Giữ chỗ trước và thanh toán trực tiếp khi đến sân.",
    available: true,
  },
  {
    key: "BANK_QR",
    label: "Chuyển khoản / QR",
    description: "Tạo giao dịch và xác nhận qua payment-service.",
    available: true,
  },
  {
    key: "DEPOSIT_ONLINE",
    label: "Thanh toán cọc online",
    description: "Đặt cọc tối thiểu 50% để xác nhận booking.",
    available: true,
  },
];

function normalizeName(value: string) {
  return value.trim().replace(/\s+/g, " ");
}

function normalizePhone(value: string) {
  return value.trim().replace(/[\s.-]/g, "");
}

function hasCustomerErrors(errors: CustomerFormErrors) {
  return Boolean(errors.contactName || errors.phone);
}

function validateCustomerInfo(contactName: string, phone: string): CustomerFormErrors {
  const errors: CustomerFormErrors = {};
  const normalizedName = normalizeName(contactName);
  const normalizedPhone = normalizePhone(phone);

  if (!normalizedName) {
    errors.contactName = "Vui lòng nhập họ và tên.";
  } else if (normalizedName.length < 2) {
    errors.contactName = "Họ và tên phải có ít nhất 2 ký tự.";
  }

  if (!normalizedPhone) {
    errors.phone = "Vui lòng nhập số điện thoại.";
  } else if (!/^(0\d{9}|\+84\d{9}|84\d{9})$/.test(normalizedPhone)) {
    errors.phone = "Số điện thoại không hợp lệ. Ví dụ: 0914539824 hoặc +84914539824.";
  }

  return errors;
}

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
  const [customerErrors, setCustomerErrors] = useState<CustomerFormErrors>({});
  const [selectedMethod, setSelectedMethod] = useState<PaymentMethodKey>("BANK_QR");
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
    return Math.max(0, ((endHour * 60 + endMinute) - (startHour * 60 + startMinute)) / 60);
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

        const quoteError = quoteResult.reason;
        const uiError = toErrorPresentation(quoteError, "Không lấy được báo giá");
        setQuote(null);
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);

        if (quoteError instanceof ApiRequestError && quoteError.code === "PRICING_RULE_MISSING") {
          setPricingUnavailable(true);
        }
      } finally {
        setLoading(false);
      }
    }

    void loadQuoteAndAvailability();
  }, [courtId, date, end, start]);

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

  function handleContactNameChange(value: string) {
    setContactName(value);
    if (customerErrors.contactName) {
      setCustomerErrors((current) => ({ ...current, contactName: undefined }));
    }
  }

  function handlePhoneChange(value: string) {
    setPhone(value);
    if (customerErrors.phone) {
      setCustomerErrors((current) => ({ ...current, phone: undefined }));
    }
  }

  async function handleConfirm() {
    if (!date || !start || !end || !courtId) {
      setError("Thiếu thông tin khung giờ.");
      return;
    }
    if (!isAuthenticated || !token?.accessToken) {
      const redirect = encodeURIComponent(window.location.pathname + window.location.search);
      navigate(`/auth/login?redirect=${redirect}`);
      return;
    }

    const validationErrors = validateCustomerInfo(contactName, phone);
    setCustomerErrors(validationErrors);
    if (hasCustomerErrors(validationErrors)) {
      setError("Vui lòng kiểm tra thông tin khách hàng.");
      setTraceId(null);
      return;
    }

    if (available === false) {
      setError("Khung giờ đã có người đặt. Vui lòng chọn khung giờ khác.");
      return;
    }
    if (quote === null || pricingUnavailable) {
      setError("Khung giờ này chưa có bảng giá. Vui lòng chọn khung giờ khác.");
      return;
    }

    try {
      setLoading(true);
      setError(null);
      setTraceId(null);
      setContactName(normalizeName(contactName));
      setPhone(normalizePhone(phone));

      const booking = await createBookingDraft({
        courtId,
        startTime: buildOffsetIso(date, start),
        endTime: buildOffsetIso(date, end),
        priceTotal: quote,
      }, createIdempotencyKey("checkout-draft"));

      trackEvent("checkout_draft_created", {
        bookingId: booking.id,
        method: selectedMethod,
        quote,
      });

      if (selectedMethod === "AT_VENUE") {
        navigate(`/booking/success/${booking.id}?mode=AT_VENUE`);
      } else {
        navigate(`/payment/${booking.id}`);
      }
    } catch (err) {
      const uiError = toErrorPresentation(err, "Tạo booking thất bại");
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
        <Link to={`/booking/grid?venueId=${venueId}&courtId=${courtId}&date=${date}`} className="back-link">←</Link>
        <h1>Xác nhận đặt sân</h1>
        <div className="topbar-spacer" />
      </header>

      <section className="checkout-panels">
        <article className="checkout-card">
          <h2>Thông tin sân</h2>
          <p><strong>Tên cụm sân:</strong> {selectedVenue?.name ?? "-"}</p>
          <p><strong>Địa chỉ:</strong> {selectedVenue?.address ?? "-"}</p>
          <p><strong>Sân:</strong> {selectedCourt?.name ?? "-"}</p>
        </article>

        <article className="checkout-card">
          <h2>Tóm tắt lịch đặt</h2>
          <p><strong>Ngày:</strong> {date || "-"}</p>
          <p><strong>Khung giờ:</strong> {start || "-"} - {end || "-"}</p>
          <p><strong>Số slot:</strong> {slotCount} slot (30 phút/slot)</p>
          <p><strong>Tổng thời lượng:</strong> {durationHours > 0 ? `${durationHours.toFixed(1)} giờ` : "-"}</p>
          <p><strong>Tổng tiền:</strong> {quote !== null ? formatCurrency(quote) : "Đang tính..."}</p>
          <p><strong>Tiền cọc tối thiểu:</strong> {quote !== null ? formatCurrency(estimatedDeposit) : "Đang tính..."}</p>
          <p><strong>Trạng thái slot:</strong> {available === null ? "Đang kiểm tra" : available ? "Còn trống" : "Đã được đặt"}</p>
        </article>
      </section>

      <section className="checkout-methods-preview">
        <h3>Phương thức thanh toán</h3>
        <PaymentMethodSelector
          options={paymentOptions}
          value={selectedMethod}
          onChange={(value) => setSelectedMethod(value as PaymentMethodKey)}
        />
      </section>

      <section className="checkout-form" aria-label="Thông tin khách hàng">
        <label htmlFor="checkout-contact-name">
          HỌ VÀ TÊN <span className="required-mark">*</span>
          <input
            id="checkout-contact-name"
            className={customerErrors.contactName ? "checkout-input--error" : undefined}
            value={contactName}
            onChange={(event) => handleContactNameChange(event.target.value)}
            placeholder="Nhập họ và tên"
            autoComplete="name"
            aria-invalid={Boolean(customerErrors.contactName)}
            aria-describedby={customerErrors.contactName ? "checkout-contact-name-error" : undefined}
            required
          />
          {customerErrors.contactName ? (
            <span id="checkout-contact-name-error" className="ui-field__error">{customerErrors.contactName}</span>
          ) : null}
        </label>
        <label htmlFor="checkout-phone">
          SỐ ĐIỆN THOẠI <span className="required-mark">*</span>
          <input
            id="checkout-phone"
            className={customerErrors.phone ? "checkout-input--error" : undefined}
            value={phone}
            onChange={(event) => handlePhoneChange(event.target.value)}
            placeholder="Nhập số điện thoại"
            inputMode="tel"
            autoComplete="tel"
            aria-invalid={Boolean(customerErrors.phone)}
            aria-describedby={customerErrors.phone ? "checkout-phone-error" : undefined}
            required
          />
          {customerErrors.phone ? (
            <span id="checkout-phone-error" className="ui-field__error">{customerErrors.phone}</span>
          ) : null}
        </label>
        <label htmlFor="checkout-note">
          GHI CHÚ CHO CHỦ SÂN
          <textarea
            id="checkout-note"
            value={note}
            onChange={(event) => setNote(event.target.value)}
            placeholder="Nhập ghi chú (tuỳ chọn)"
          />
        </label>
      </section>

      <section className="checkout-policy-note">
        <strong>Chính sách:</strong> Booking chỉ được xác nhận khi thanh toán/cọc hợp lệ theo quy định của sân.
      </section>

      {error && <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p>}
      {loading && <p className="inline-muted">Đang xử lý...</p>}

      <div className="checkout-footer">
        <Button variant="primary" size="lg" fullWidth onClick={() => { void handleConfirm(); }} disabled={confirmDisabled}>
          XÁC NHẬN ĐẶT SÂN
        </Button>
      </div>
    </div>
  );
}
