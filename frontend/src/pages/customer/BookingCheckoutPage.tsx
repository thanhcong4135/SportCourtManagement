import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { BottomNavigation } from "../../components/BottomNavigation";
import { useAuth } from "../../context/AuthContext";
import { formatCurrency } from "../../lib/api";
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
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | null>(null);

  const selectedVenue = useMemo(() => venues.find((item) => item.id === venueId) ?? null, [venueId, venues]);
  const selectedCourt = useMemo(() => courts.find((item) => item.id === courtId) ?? null, [courtId, courts]);

  useEffect(() => {
    if (!date || !start || !end || !courtId) {
      return;
    }

    async function loadQuoteAndAvailability() {
      try {
        setError(null);
        setTraceId(null);
        setLoading(true);
        const startIso = buildOffsetIso(date, start);
        const endIso = buildOffsetIso(date, end);
        const [availabilityResp, quoteResp] = await Promise.all([
          checkAvailability(courtId, startIso, endIso),
          quoteBooking(courtId, startIso, endIso),
        ]);
        setAvailable(availabilityResp.available);
        setQuote(quoteResp.totalPrice);
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
      navigate(`/auth/login?redirect=${redirect}`);
      return;
    }
    if (available === false) {
      setError("Khung giờ đã có người đặt. Hãy chọn khung giờ khác.");
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
        priceTotal: quote ?? 0,
      });

      navigate(`/payment/${booking.id}`);
    } catch (err) {
      const uiError = toErrorPresentation(err, "Tạo booking nháp thất bại");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setLoading(false);
    }
  }

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
          <p><strong>Tổng tiền:</strong> {quote !== null ? formatCurrency(quote) : "Đang tính..."}</p>
          <p><strong>Trạng thái:</strong> {available === null ? "Đang kiểm tra" : available ? "Còn trống" : "Đã được đặt"}</p>
        </article>
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

      {error && <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p>}
      {loading && <p className="inline-muted">Đang xử lý...</p>}

      <button type="button" className="primary-bottom-btn" onClick={() => { void handleConfirmAndPay(); }}>
        XÁC NHẬN & THANH TOÁN
      </button>

      <BottomNavigation active="booking" />
    </div>
  );
}

