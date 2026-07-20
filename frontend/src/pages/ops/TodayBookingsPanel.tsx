import { formatCurrency } from "../../lib/api";
import type { Booking, Court } from "../../lib/coreApi";
import { toLocalDateTime } from "../../lib/coreApi";
import { BookingStatusBadge } from "./BookingStatusBadge";

type Props = {
  bookings: Booking[];
  courts: Court[];
  loading?: boolean;
  onSelectBooking: (booking: Booking) => void;
};

function shortTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return "--:--";
  }
  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

export function TodayBookingsPanel({ bookings, courts, loading = false, onSelectBooking }: Props) {
  const courtById = new Map(courts.map((court) => [court.id, court]));

  return (
    <article className="card today-bookings-panel">
      <header className="ops-section-head">
        <div>
          <h3>Danh sách Booking trong ngày</h3>
        </div>
        <strong>{bookings.length}</strong>
      </header>

      {loading ? <p className="inline-muted">Đang tải booking...</p> : null}

      <div className="ops-table-wrap today-bookings-table">
        <table className="ops-table">
          <thead>
            <tr>
              <th>Thời gian</th>
              <th>Sân</th>
              <th>Khách</th>
              <th>Trạng thái</th>
              <th>Tổng đơn</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {bookings.map((booking) => {
              const court = courtById.get(booking.courtId);
              return (
                <tr key={booking.id}>
                  <td>{shortTime(booking.startTime)} - {shortTime(booking.endTime)}</td>
                  <td>{court?.name ?? booking.courtId}</td>
                  <td><code>{booking.customerId.slice(0, 8)}</code></td>
                  <td><BookingStatusBadge status={booking.status} paymentStatus={booking.paymentStatus} compact /></td>
                  <td>{formatCurrency(booking.priceTotal)}</td>
                  <td>
                    <button type="button" className="btn ghost" onClick={() => onSelectBooking(booking)}>
                      Chi tiet
                    </button>
                  </td>
                </tr>
              );
            })}
            {!bookings.length ? (
              <tr><td colSpan={6} className="muted">Chưa có booking nào trong ngày đã chọn.</td></tr>
            ) : null}
          </tbody>
        </table>
      </div>

      <div className="today-booking-cards">
        {bookings.map((booking) => {
          const court = courtById.get(booking.courtId);
          return (
            <button key={booking.id} type="button" className="today-booking-card" onClick={() => onSelectBooking(booking)}>
              <span>{toLocalDateTime(booking.startTime)} - {shortTime(booking.endTime)}</span>
              <strong>{court?.name ?? "San khong ro"}</strong>
              <BookingStatusBadge status={booking.status} paymentStatus={booking.paymentStatus} compact />
              <small>{formatCurrency(booking.priceTotal)}</small>
            </button>
          );
        })}
        {!bookings.length ? <p className="inline-muted">Chua co booking trong ngay.</p> : null}
      </div>
    </article>
  );
}
