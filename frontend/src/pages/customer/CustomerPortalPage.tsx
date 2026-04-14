import { useCallback, useEffect, useMemo, useState } from "react";
import { apiFetch, formatCurrency, toIsoWithOffset } from "../../lib/api";
import { useAuth } from "../../context/AuthContext";

type Venue = { id: string; name: string; address: string };
type Court = { id: string; venueId: string; name: string; sportType: string };
type Booking = {
  id: string;
  courtId: string;
  customerId: string;
  status: string;
  paymentStatus: string;
  startTime: string;
  endTime: string;
  priceTotal: number;
  depositRequired: number;
  depositPaid: number;
};
type BookingPage = { items: Booking[] };
type Product = { id: string; venueId: string; name: string; unitPrice: number; active: boolean };
type PricingQuote = { totalPrice: number };

const visibleHours = Array.from({ length: 17 }, (_, idx) => idx + 6); // 06:00 -> 22:00

export function CustomerPortalPage() {
  const { token, isAuthenticated } = useAuth();

  const [venues, setVenues] = useState<Venue[]>([]);
  const [courts, setCourts] = useState<Court[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [bookings, setBookings] = useState<Booking[]>([]);

  const [selectedVenueId, setSelectedVenueId] = useState("");
  const [selectedCourtId, setSelectedCourtId] = useState("");
  const [startTimeLocal, setStartTimeLocal] = useState("");
  const [endTimeLocal, setEndTimeLocal] = useState("");
  const [quote, setQuote] = useState<PricingQuote | null>(null);
  const [available, setAvailable] = useState<boolean | null>(null);

  const [selectedBookingId, setSelectedBookingId] = useState("");
  const [selectedProductId, setSelectedProductId] = useState("");
  const [orderQuantity, setOrderQuantity] = useState(1);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const selectedCourt = useMemo(() => courts.find((item) => item.id === selectedCourtId) ?? null, [courts, selectedCourtId]);
  const selectedBooking = useMemo(() => bookings.find((item) => item.id === selectedBookingId) ?? null, [bookings, selectedBookingId]);

  const loadVenues = useCallback(async () => {
    try {
      const data = await apiFetch<Venue[]>("/api/core/venues");
      setVenues(data);
      if (!selectedVenueId && data[0]) {
        setSelectedVenueId(data[0].id);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Khong tai duoc venues");
    }
  }, [selectedVenueId]);

  const loadCourts = useCallback(async (venueId: string) => {
    try {
      const data = await apiFetch<Court[]>(`/api/core/courts?venueId=${venueId}`);
      setCourts(data);
      if (!selectedCourtId && data[0]) {
        setSelectedCourtId(data[0].id);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Khong tai duoc courts");
    }
  }, [selectedCourtId]);

  const loadProducts = useCallback(async (venueId: string) => {
    try {
      const data = await apiFetch<Product[]>(`/api/core/products?venueId=${venueId}&activeOnly=true`);
      setProducts(data);
      if (!selectedProductId && data[0]) {
        setSelectedProductId(data[0].id);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Khong tai duoc products");
    }
  }, [selectedProductId]);

  const loadBookings = useCallback(async () => {
    if (!token?.accessToken) {
      return;
    }
    try {
      const data = await apiFetch<BookingPage>("/api/core/bookings?page=0&size=20", {}, token.accessToken);
      setBookings(data.items || []);
      if (!selectedBookingId && data.items?.[0]) {
        setSelectedBookingId(data.items[0].id);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Khong tai duoc bookings");
    }
  }, [selectedBookingId, token?.accessToken]);

  useEffect(() => {
    void loadVenues();
  }, [loadVenues]);

  useEffect(() => {
    if (!selectedVenueId) {
      setCourts([]);
      setProducts([]);
      return;
    }
    void loadCourts(selectedVenueId);
    void loadProducts(selectedVenueId);
  }, [loadCourts, loadProducts, selectedVenueId]);

  useEffect(() => {
    if (isAuthenticated) {
      void loadBookings();
    } else {
      setBookings([]);
      setSelectedBookingId("");
    }
  }, [isAuthenticated, loadBookings]);

  const scheduleDate = useMemo(() => {
    if (startTimeLocal) {
      return startTimeLocal.slice(0, 10);
    }
    return new Date().toISOString().slice(0, 10);
  }, [startTimeLocal]);

  const bookingsOnSelectedCourt = useMemo(() => {
    if (!selectedCourtId) {
      return [];
    }
    return bookings.filter((booking) => booking.courtId === selectedCourtId && booking.startTime.startsWith(scheduleDate));
  }, [bookings, selectedCourtId, scheduleDate]);

  function isBusyAtHour(hour: number): Booking | null {
    return bookingsOnSelectedCourt.find((booking) => {
      const start = new Date(booking.startTime).getHours();
      const end = new Date(booking.endTime).getHours();
      return hour >= start && hour < end;
    }) ?? null;
  }

  function requireBookingInputs() {
    if (!selectedCourtId || !startTimeLocal || !endTimeLocal) {
      throw new Error("Can chon court + start + end");
    }
  }

  async function checkAvailabilityAndQuote() {
    try {
      requireBookingInputs();
      setBusy(true);
      setError(null);
      const start = toIsoWithOffset(startTimeLocal);
      const end = toIsoWithOffset(endTimeLocal);

      const [availabilityResp, quoteResp] = await Promise.all([
        apiFetch<{ available: boolean }>(`/api/core/availability?courtId=${selectedCourtId}&start=${encodeURIComponent(start)}&end=${encodeURIComponent(end)}`),
        apiFetch<PricingQuote>(`/api/core/pricing/quote?courtId=${selectedCourtId}&start=${encodeURIComponent(start)}&end=${encodeURIComponent(end)}&customerTier=STANDARD`),
      ]);

      setAvailable(availabilityResp.available);
      setQuote(quoteResp);
      setNotice("Da cap nhat availability + quote.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Khong quote duoc");
    } finally {
      setBusy(false);
    }
  }

  async function createBookingDraft() {
    if (!token?.accessToken) {
      setError("Can dang nhap de tao booking.");
      return;
    }
    try {
      requireBookingInputs();
      setBusy(true);
      setError(null);

      const startTime = toIsoWithOffset(startTimeLocal);
      const endTime = toIsoWithOffset(endTimeLocal);
      const priceTotal = quote?.totalPrice ?? 100000;

      await apiFetch<Booking>("/api/core/bookings/draft", {
        method: "POST",
        body: JSON.stringify({ courtId: selectedCourtId, startTime, endTime, priceTotal }),
      }, token.accessToken);

      await loadBookings();
      setNotice("Da tao booking draft.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Khong tao duoc draft");
    } finally {
      setBusy(false);
    }
  }

  async function depositAndConfirm(booking: Booking) {
    if (!token?.accessToken) {
      return;
    }
    try {
      setBusy(true);
      setError(null);
      await apiFetch<Booking>(`/api/core/bookings/${booking.id}/deposit`, {
        method: "POST",
        body: JSON.stringify({ amount: booking.depositRequired }),
      }, token.accessToken);

      await apiFetch<Booking>(`/api/core/bookings/${booking.id}/confirm`, {
        method: "POST",
      }, token.accessToken);

      await loadBookings();
      setNotice(`Da dat coc va confirm booking ${booking.id.slice(0, 8)}.`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Dat coc/confirm that bai");
    } finally {
      setBusy(false);
    }
  }

  async function createOrder() {
    if (!token?.accessToken) {
      setError("Can dang nhap de tao order.");
      return;
    }
    if (!selectedBookingId || !selectedProductId || !selectedVenueId) {
      setError("Can chon booking + product + venue");
      return;
    }

    try {
      setBusy(true);
      setError(null);
      await apiFetch("/api/core/orders", {
        method: "POST",
        body: JSON.stringify({
          bookingId: selectedBookingId,
          venueId: selectedVenueId,
          items: [{ productId: selectedProductId, quantity: orderQuantity }],
        }),
      }, token.accessToken);

      setNotice("Da tao order add-on.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Tao order that bai");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="page customer-layout-page">
      <section className="section-header">
        <p className="eyebrow">Customer Booking Board</p>
        <h1>Giao dien theo style Alobo: lich truc quan + panel dat san + panel thanh toan</h1>
        <p className="muted">Flow API van giu nguyen theo backend (availability, quote, draft, deposit, confirm, add-on).</p>
      </section>

      <section className="customer-shell">
        <article className="card customer-left">
          <h3>Bo loc dat san</h3>
          <label>
            Venue
            <select value={selectedVenueId} onChange={(e) => setSelectedVenueId(e.target.value)}>
              <option value="">-- chon venue --</option>
              {venues.map((venue) => <option value={venue.id} key={venue.id}>{venue.name}</option>)}
            </select>
          </label>

          <label>
            Court
            <select value={selectedCourtId} onChange={(e) => setSelectedCourtId(e.target.value)}>
              <option value="">-- chon court --</option>
              {courts.map((court) => <option value={court.id} key={court.id}>{court.name} ({court.sportType})</option>)}
            </select>
          </label>

          <label>
            Bat dau
            <input type="datetime-local" value={startTimeLocal} onChange={(e) => setStartTimeLocal(e.target.value)} />
          </label>
          <label>
            Ket thuc
            <input type="datetime-local" value={endTimeLocal} onChange={(e) => setEndTimeLocal(e.target.value)} />
          </label>

          <button className="btn" onClick={() => { void checkAvailabilityAndQuote(); }} disabled={busy}>Kiem tra + Quote</button>

          <div className="legend-grid">
            <span><i className="dot available" /> O trong</span>
            <span><i className="dot busy" /> Da dat</span>
            <span><i className="dot selected" /> Dang chon</span>
          </div>
        </article>

        <article className="card customer-board">
          <div className="board-head">
            <h3>Time-grid ({selectedCourt?.name ?? "Chua chon san"})</h3>
            <span className="muted">Ngay {scheduleDate}</span>
          </div>

          <div className="time-grid">
            {visibleHours.map((hour) => {
              const booking = isBusyAtHour(hour);
              return (
                <div key={hour} className={`time-cell ${booking ? "is-busy" : "is-free"}`}>
                  <div className="time-label">{String(hour).padStart(2, "0")}:00</div>
                  <div className="time-content">
                    {booking ? (
                      <>
                        <strong>{booking.status}</strong>
                        <span>{formatCurrency(booking.priceTotal)}</span>
                      </>
                    ) : (
                      <span>Trong</span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </article>

        <article className="card customer-right">
          <h3>Tom tat dat san</h3>
          <p className="muted">Court: {selectedCourt?.name ?? "-"}</p>
          <p className="muted">Availability: {available === null ? "-" : available ? "Trong" : "Da co nguoi dat"}</p>
          <p><strong>Gia du kien:</strong> {quote ? formatCurrency(quote.totalPrice) : "-"}</p>
          <button className="btn" onClick={() => { void createBookingDraft(); }} disabled={busy || !isAuthenticated}>Tao booking draft</button>

          {selectedBooking && (
            <div className="summary-box">
              <p><strong>Booking da chon</strong></p>
              <p className="muted">{selectedBooking.id.slice(0, 8)} · {selectedBooking.status}</p>
              <p>{formatCurrency(selectedBooking.priceTotal)}</p>
              <button className="btn ghost" onClick={() => { void depositAndConfirm(selectedBooking); }} disabled={busy}>Dat coc + Confirm</button>
            </div>
          )}
        </article>
      </section>

      <section className="grid two">
        <article className="card">
          <h3>Lich da dat (Account)</h3>
          {!isAuthenticated && <p className="muted">Dang nhap de xem lich da dat.</p>}
          <div className="booking-list">
            {bookings.map((booking) => (
              <button className={`booking-item ${selectedBookingId === booking.id ? "active" : ""}`} key={booking.id} onClick={() => setSelectedBookingId(booking.id)}>
                <div>
                  <p><strong>{booking.id.slice(0, 8)}</strong> · Court {booking.courtId.slice(0, 6)}</p>
                  <p className="muted">{new Date(booking.startTime).toLocaleString()} den {new Date(booking.endTime).toLocaleString()}</p>
                  <p className="muted">{booking.status} / {booking.paymentStatus}</p>
                </div>
                <div><p>{formatCurrency(booking.priceTotal)}</p></div>
              </button>
            ))}
            {!bookings.length && <p className="muted">Chua co booking.</p>}
          </div>
        </article>

        <article className="card">
          <h3>Add-on / Dich vu bo sung</h3>
          <label>
            Booking
            <select value={selectedBookingId} onChange={(e) => setSelectedBookingId(e.target.value)}>
              <option value="">-- chon booking --</option>
              {bookings.map((booking) => <option key={booking.id} value={booking.id}>{booking.id.slice(0, 8)} ({booking.status})</option>)}
            </select>
          </label>
          <label>
            Product
            <select value={selectedProductId} onChange={(e) => setSelectedProductId(e.target.value)}>
              <option value="">-- chon product --</option>
              {products.map((product) => <option key={product.id} value={product.id}>{product.name} · {formatCurrency(product.unitPrice)}</option>)}
            </select>
          </label>
          <label>
            So luong
            <input type="number" min={1} value={orderQuantity} onChange={(e) => setOrderQuantity(Number(e.target.value))} />
          </label>
          <button className="btn" onClick={() => { void createOrder(); }} disabled={busy || !isAuthenticated}>Tao order add-on</button>
        </article>
      </section>

      {(error || notice) && (
        <section className="toast-stack">
          {error && <p className="toast error-text">{error}</p>}
          {notice && <p className="toast success-text">{notice}</p>}
        </section>
      )}
    </main>
  );
}
