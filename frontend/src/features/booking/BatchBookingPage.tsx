import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { formatCurrency } from "../../lib/api";
import { toErrorPresentation } from "../../lib/errorPresentation";
import {
  buildOffsetIso,
  checkAvailability,
  confirmBookingBatch,
  createBookingDraftBatch,
  depositBookingBatch,
  listCourts,
  listVenues,
  quoteBooking,
  type BatchBookingActionResponse,
  type Court,
  type Venue,
} from "../../lib/coreApi";

type DraftItemForm = {
  courtId: string;
  date: string;
  start: string;
  end: string;
  priceTotal: number;
};

const initialItem: DraftItemForm = {
  courtId: "",
  date: new Date().toISOString().slice(0, 10),
  start: "08:00",
  end: "10:00",
  priceTotal: 0,
};

export function BatchBookingPage() {
  const [venueId, setVenueId] = useState("");
  const [venues, setVenues] = useState<Venue[]>([]);
  const [courts, setCourts] = useState<Court[]>([]);
  const [items, setItems] = useState<DraftItemForm[]>([{ ...initialItem }]);
  const [draftResult, setDraftResult] = useState<BatchBookingActionResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    async function loadVenues() {
      try {
        const rows = await listVenues();
        setVenues(rows);
        if (!venueId && rows[0]) {
          setVenueId(rows[0].id);
        }
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được danh sách cụm sân");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      }
    }
    void loadVenues();
  }, [venueId]);

  useEffect(() => {
    if (!venueId) {
      setCourts([]);
      return;
    }
    async function loadCourtsByVenue() {
      try {
        const rows = await listCourts(venueId);
        setCourts(rows);
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được danh sách sân");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      }
    }
    void loadCourtsByVenue();
  }, [venueId]);

  const draftBookingIds = useMemo(() => draftResult?.bookings.map((booking) => booking.id) ?? [], [draftResult]);

  function updateItem(index: number, patch: Partial<DraftItemForm>) {
    setItems((prev) => prev.map((item, current) => (current === index ? { ...item, ...patch } : item)));
  }

  function addItem() {
    setItems((prev) => [...prev, { ...initialItem }]);
  }

  function removeItem(index: number) {
    setItems((prev) => prev.filter((_, current) => current !== index));
  }

  async function handleQuote(index: number) {
    const item = items[index];
    if (!item.courtId || !item.date || !item.start || !item.end) {
      setError("Thiếu dữ liệu để tính giá và kiểm tra lịch trống.");
      setTraceId(null);
      return;
    }

    const startIso = buildOffsetIso(item.date, item.start);
    const endIso = buildOffsetIso(item.date, item.end);

    try {
      setBusy(true);
      setError(null);
      setTraceId(null);
      const [availability, quote] = await Promise.all([
        checkAvailability(item.courtId, startIso, endIso),
        quoteBooking(item.courtId, startIso, endIso),
      ]);
      if (!availability.available) {
        setError(`Sân ở dòng ${index + 1} đã có người đặt trong khung giờ này.`);
        return;
      }
      updateItem(index, { priceTotal: quote.totalPrice });
      setNotice(`Đã cập nhật giá cho dòng ${index + 1}.`);
    } catch (err) {
      const uiError = toErrorPresentation(err, "Không tính được giá");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  async function handleCreateDraftBatch() {
    try {
      setBusy(true);
      setError(null);
      setTraceId(null);
      setNotice(null);
      const payload = {
        items: items.map((item) => ({
          courtId: item.courtId,
          startTime: buildOffsetIso(item.date, item.start),
          endTime: buildOffsetIso(item.date, item.end),
          priceTotal: item.priceTotal,
        })),
      };
      const result = await createBookingDraftBatch(payload);
      setDraftResult(result);
      setNotice(`Đã tạo ${result.bookings.length} booking nháp.`);
    } catch (err) {
      const uiError = toErrorPresentation(err, "Tạo batch draft thất bại");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  async function handleDepositBatch() {
    if (!draftResult?.bookings.length) {
      setError("Cần tạo batch draft trước.");
      setTraceId(null);
      return;
    }
    try {
      setBusy(true);
      setError(null);
      setTraceId(null);
      const result = await depositBookingBatch({
        items: draftResult.bookings.map((booking) => ({
          bookingId: booking.id,
          amount: booking.depositRequired,
        })),
      });
      setDraftResult(result);
      setNotice("Đã đặt cọc batch thành công.");
    } catch (err) {
      const uiError = toErrorPresentation(err, "Đặt cọc batch thất bại");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  async function handleConfirmBatch() {
    if (!draftBookingIds.length) {
      setError("Cần có booking để xác nhận.");
      setTraceId(null);
      return;
    }
    try {
      setBusy(true);
      setError(null);
      setTraceId(null);
      const result = await confirmBookingBatch({ bookingIds: draftBookingIds });
      setDraftResult(result);
      setNotice("Đã xác nhận toàn bộ booking trong nhóm.");
    } catch (err) {
      const uiError = toErrorPresentation(err, "Confirm batch thất bại");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="alobo-screen batch-booking-screen">
      <header className="simple-topbar">
        <Link to="/discover" className="back-link">←</Link>
        <h1>Đặt nhiều sân (Batch)</h1>
        <div className="topbar-spacer" />
      </header>

      <section className="card">
        <label>
          Cụm sân
          <select value={venueId} onChange={(event) => setVenueId(event.target.value)}>
            <option value="">-- Chọn cụm sân --</option>
            {venues.map((venue) => (
              <option key={venue.id} value={venue.id}>{venue.name}</option>
            ))}
          </select>
        </label>
      </section>

      <section className="card">
        <h3>Danh sách khung giờ</h3>
        <div className="list-clean">
          {items.map((item, index) => (
            <article key={`item-${index}`} className="card inner-card">
              <div className="grid two">
                <label>
                  Sân
                  <select value={item.courtId} onChange={(event) => updateItem(index, { courtId: event.target.value })}>
                    <option value="">-- Chọn sân --</option>
                    {courts.map((court) => (
                      <option key={court.id} value={court.id}>{court.name}</option>
                    ))}
                  </select>
                </label>
                <label>
                  Ngày
                  <input type="date" value={item.date} onChange={(event) => updateItem(index, { date: event.target.value })} />
                </label>
                <label>
                  Bắt đầu
                  <input type="time" value={item.start} onChange={(event) => updateItem(index, { start: event.target.value })} />
                </label>
                <label>
                  Kết thúc
                  <input type="time" value={item.end} onChange={(event) => updateItem(index, { end: event.target.value })} />
                </label>
              </div>
              <div className="ops-toolbar">
                <button className="btn ghost" type="button" onClick={() => { void handleQuote(index); }} disabled={busy}>
                  Tính giá
                </button>
                <span className="muted">Giá hiện tại: {formatCurrency(item.priceTotal)}</span>
                {items.length > 1 && (
                  <button className="btn ghost" type="button" onClick={() => removeItem(index)} disabled={busy}>
                    Xóa dòng
                  </button>
                )}
              </div>
            </article>
          ))}
        </div>
        <button className="btn ghost" type="button" onClick={addItem} disabled={busy}>
          + Thêm khung giờ
        </button>
      </section>

      <section className="card">
        <h3>Flow nhóm booking</h3>
        <div className="ops-toolbar">
          <button className="btn" type="button" onClick={() => { void handleCreateDraftBatch(); }} disabled={busy}>
            1) Tạo Batch Draft
          </button>
          <button className="btn" type="button" onClick={() => { void handleDepositBatch(); }} disabled={busy || !draftBookingIds.length}>
            2) Batch Deposit
          </button>
          <button className="btn" type="button" onClick={() => { void handleConfirmBatch(); }} disabled={busy || !draftBookingIds.length}>
            3) Batch Confirm
          </button>
        </div>
        {draftResult && (
          <div className="payment-list-box">
            <p><strong>Số booking:</strong> {draftResult.bookings.length}</p>
            <p><strong>Tổng tiền:</strong> {formatCurrency(draftResult.totalPrice)}</p>
            <p><strong>Tổng cọc yêu cầu:</strong> {formatCurrency(draftResult.totalDepositRequired)}</p>
            <p><strong>Tổng đã cọc:</strong> {formatCurrency(draftResult.totalDepositPaid)}</p>
            <ul className="list-clean compact-list">
              {draftResult.bookings.map((booking) => (
                <li key={booking.id}>
                  #{booking.id.slice(0, 8)} · {booking.status} · {booking.paymentStatus}
                </li>
              ))}
            </ul>
          </div>
        )}
      </section>

      {error && <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p>}
      {notice && <p className="inline-success">{notice}</p>}

    </div>
  );
}
