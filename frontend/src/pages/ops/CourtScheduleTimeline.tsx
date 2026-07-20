import type { Booking, Court } from "../../lib/coreApi";
import { formatCurrency } from "../../lib/api";
import { toLocalDateTime } from "../../lib/coreApi";
import { BookingStatusBadge } from "./BookingStatusBadge";

type Props = {
  courts: Court[];
  bookings: Booking[];
  date: string;
  loading?: boolean;
  onSelectBooking: (booking: Booking) => void;
};

const minutesPerDay = 24 * 60;
const hours = Array.from({ length: 24 }, (_, index) => index);

function minutesFromDayStart(iso: string): number {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return 0;
  }
  return date.getHours() * 60 + date.getMinutes();
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

function getBookingStyle(booking: Booking) {
  const start = clamp(minutesFromDayStart(booking.startTime), 0, minutesPerDay);
  const end = clamp(minutesFromDayStart(booking.endTime), start + 15, minutesPerDay);
  return {
    left: `${(start / minutesPerDay) * 100}%`,
    width: `${Math.max(((end - start) / minutesPerDay) * 100, 1.8)}%`,
  };
}

function statusClass(booking: Booking): string {
  return [
    "booking-block",
    `booking-block--${booking.status.toLowerCase().replace("_", "-")}`,
    `booking-block--payment-${booking.paymentStatus.toLowerCase()}`,
  ].join(" ");
}

function formatTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return "--:--";
  }
  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}

export function CourtScheduleTimeline({ courts, bookings, date, loading = false, onSelectBooking }: Props) {
  const bookingsByCourt = new Map<string, Booking[]>();
  for (const booking of bookings) {
    const courtBookings = bookingsByCourt.get(booking.courtId) ?? [];
    courtBookings.push(booking);
    bookingsByCourt.set(booking.courtId, courtBookings);
  }

  return (
    <article className="card ops-schedule-card">
      <header className="ops-section-head">
        <div>
          <h3>Timeline trong ngày</h3>
        </div>
        <span className="muted">{date} · {bookings.length} booking</span>
      </header>

      {loading ? <p className="inline-muted">Dang tai lich san...</p> : null}

      <div className="court-timeline-wrap" aria-label="Court schedule timeline">
        <div className="court-timeline-grid">
          <div className="court-timeline-corner">Sân</div>
          <div className="court-hour-header">
            {hours.map((hour) => (
              <span key={hour}>{String(hour).padStart(2, "0")}:00</span>
            ))}
          </div>
          {courts.map((court) => (
            <div className="court-timeline-row" key={court.id}>
              <div className="court-row-label">
                <strong>{court.name}</strong>
                <small>{court.sportType}</small>
              </div>
              <div className="court-row-track">
                {hours.map((hour) => <span key={hour} className="court-hour-line" />)}
                {(bookingsByCourt.get(court.id) ?? []).map((booking) => (
                  <button
                    key={booking.id}
                    type="button"
                    className={statusClass(booking)}
                    style={getBookingStyle(booking)}
                    onClick={() => onSelectBooking(booking)}
                    title={`${formatTime(booking.startTime)} - ${formatTime(booking.endTime)}`}
                  >
                    <strong>{formatTime(booking.startTime)}-{formatTime(booking.endTime)}</strong>
                    <span>{formatCurrency(booking.priceTotal)}</span>
                  </button>
                ))}
              </div>
            </div>
          ))}
          {!courts.length ? <p className="inline-muted court-timeline-empty">Chua co san trong venue nay.</p> : null}
        </div>
      </div>

      <div className="court-mobile-schedule">
        {courts.map((court) => {
          const courtBookings = bookingsByCourt.get(court.id) ?? [];
          return (
            <section key={court.id} className="court-mobile-group">
              <header>
                <strong>{court.name}</strong>
                <span>{courtBookings.length} booking</span>
              </header>
              {courtBookings.map((booking) => (
                <button key={booking.id} type="button" className="court-mobile-booking" onClick={() => onSelectBooking(booking)}>
                  <span>{toLocalDateTime(booking.startTime)} - {formatTime(booking.endTime)}</span>
                  <BookingStatusBadge status={booking.status} paymentStatus={booking.paymentStatus} compact />
                </button>
              ))}
              {!courtBookings.length ? <p className="muted">Chua co booking.</p> : null}
            </section>
          );
        })}
      </div>
    </article>
  );
}
