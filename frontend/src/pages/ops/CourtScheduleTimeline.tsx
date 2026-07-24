import type { CSSProperties } from "react";
import type { Booking, Court } from "../../lib/coreApi";
import { formatCurrency } from "../../lib/api";
import { toLocalDateTime } from "../../lib/coreApi";
import { BookingStatusBadge } from "./BookingStatusBadge";

type Props = {
  courts: Court[];
  bookings: Booking[];
  date: string;
  openingTime?: string | null;
  closingTime?: string | null;
  loading?: boolean;
  onSelectBooking: (booking: Booking) => void;
};

const minutesPerDay = 24 * 60;
const timelineSlotMinutes = 30;

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

function parseClockTime(value?: string | null): number | null {
  if (!value) {
    return null;
  }
  const [hourRaw, minuteRaw = "0"] = value.split(":");
  const hour = Number(hourRaw);
  const minute = Number(minuteRaw);
  if (!Number.isFinite(hour) || !Number.isFinite(minute) || hour < 0 || hour > 23 || minute < 0 || minute > 59) {
    return null;
  }
  return hour * 60 + minute;
}

function formatClock(minutes: number): string {
  const clamped = clamp(minutes, 0, minutesPerDay);
  const hour = Math.floor(clamped / 60);
  const minute = clamped % 60;
  return `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
}

function getTimelineRange(openingTime?: string | null, closingTime?: string | null) {
  const parsedStart = parseClockTime(openingTime);
  const parsedEnd = parseClockTime(closingTime);
  const start = clamp(parsedStart ?? 0, 0, minutesPerDay - 60);
  const end = parsedEnd && parsedEnd > start ? clamp(parsedEnd, start + 60, minutesPerDay) : minutesPerDay;
  const slotCount = Math.max(Math.ceil((end - start) / timelineSlotMinutes), 1);
  const slots = Array.from({ length: slotCount }, (_, index) => start + index * timelineSlotMinutes);
  return { start, end, slots };
}

function shouldShowHourLabel(slot: number): boolean {
  return slot % 60 === 0;
}

function getBookingStyle(booking: Booking, rangeStart: number, rangeEnd: number): CSSProperties {
  const timelineMinutes = Math.max(rangeEnd - rangeStart, 60);
  const start = clamp(minutesFromDayStart(booking.startTime), rangeStart, rangeEnd);
  const end = clamp(minutesFromDayStart(booking.endTime), start + 15, rangeEnd);
  return {
    left: `${((start - rangeStart) / timelineMinutes) * 100}%`,
    width: `${Math.max(((end - start) / timelineMinutes) * 100, 1.8)}%`,
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

export function CourtScheduleTimeline({
  courts,
  bookings,
  date,
  openingTime,
  closingTime,
  loading = false,
  onSelectBooking,
}: Props) {
  const timelineRange = getTimelineRange(openingTime, closingTime);
  const timelineSlots = timelineRange.slots;
  const timelineColumnWidth = Math.max(timelineSlots.length * 56, 56);
  const timelineGridStyle: CSSProperties = {
    minWidth: `${170 + timelineColumnWidth}px`,
    gridTemplateColumns: `170px minmax(${timelineColumnWidth}px, 1fr)`,
  };
  const timelineHeaderStyle: CSSProperties = {
    gridTemplateColumns: `repeat(${timelineSlots.length}, minmax(56px, 1fr))`,
  };
  const timelineTrackStyle = {
    "--timeline-slot-count": timelineSlots.length,
  } as CSSProperties;

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
          <h3>Timeline trong ngay</h3>
        </div>
        <span className="muted">
          {date} - {formatClock(timelineRange.start)}-{formatClock(timelineRange.end)} - {bookings.length} booking
        </span>
      </header>

      {loading ? <p className="inline-muted">Dang tai lich san...</p> : null}

      <div className="court-timeline-wrap" aria-label="Court schedule timeline">
        <div className="court-timeline-grid" style={timelineGridStyle}>
          <div className="court-timeline-corner">San</div>
          <div className="court-hour-header" style={timelineHeaderStyle}>
            {timelineSlots.map((slot, index) => {
              const isFirstSlot = index === 0;
              const isLastSlot = index === timelineSlots.length - 1;
              return (
                <span key={slot} className={isFirstSlot ? "is-first" : undefined}>
                  {shouldShowHourLabel(slot) ? <small>{formatClock(slot)}</small> : null}
                  {isLastSlot && shouldShowHourLabel(timelineRange.end) ? (
                    <small className="court-hour-end-label">{formatClock(timelineRange.end)}</small>
                  ) : null}
                </span>
              );
            })}
          </div>
          {courts.map((court) => (
            <div className="court-timeline-row" key={court.id}>
              <div className="court-row-label">
                <strong>{court.name}</strong>
                <small>{court.sportType}</small>
              </div>
              <div className="court-row-track" style={timelineTrackStyle}>
                {timelineSlots.map((slot) => (
                  <span key={slot} className="court-hour-line" />
                ))}
                {(bookingsByCourt.get(court.id) ?? []).map((booking) => (
                  <button
                    key={booking.id}
                    type="button"
                    className={statusClass(booking)}
                    style={getBookingStyle(booking, timelineRange.start, timelineRange.end)}
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
