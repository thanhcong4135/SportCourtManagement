import { useMemo, useState } from "react";
import { Button } from "../../components/ui/Button";
import { Drawer } from "../../components/ui/Drawer";
import { formatCurrency, toIsoWithOffset } from "../../lib/api";
import type { Booking, Court } from "../../lib/coreApi";
import { toLocalDateTime } from "../../lib/coreApi";
import { BookingStatusBadge } from "./BookingStatusBadge";

type BookingAction = "confirm" | "cancel" | "reschedule";

type Props = {
  booking: Booking | null;
  courts: Court[];
  open: boolean;
  busy?: boolean;
  onClose: () => void;
  onAction: (action: BookingAction, booking: Booking, payload?: { courtId?: string; startTime: string; endTime: string }) => Promise<void>;
};

function toDateTimeInputValue(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  const hour = String(date.getHours()).padStart(2, "0");
  const minute = String(date.getMinutes()).padStart(2, "0");
  return `${year}-${month}-${day}T${hour}:${minute}`;
}

export function BookingDetailDrawer({ booking, courts, open, busy = false, onClose, onAction }: Props) {
  if (!booking) {
    return <Drawer open={open} title="Chi tiet booking" onClose={onClose}><p className="muted">Chua chon booking.</p></Drawer>;
  }

  return (
    <Drawer open={open} title="Chi tiet booking" onClose={onClose}>
      <BookingDrawerContent key={booking.id} booking={booking} courts={courts} busy={busy} onAction={onAction} />
    </Drawer>
  );
}

function BookingDrawerContent({
  booking,
  courts,
  busy,
  onAction,
}: {
  booking: Booking;
  courts: Court[];
  busy: boolean;
  onAction: Props["onAction"];
}) {
  const [rescheduleOpen, setRescheduleOpen] = useState(false);
  const [courtId, setCourtId] = useState(booking.courtId);
  const [startTime, setStartTime] = useState(toDateTimeInputValue(booking.startTime));
  const [endTime, setEndTime] = useState(toDateTimeInputValue(booking.endTime));
  const court = useMemo(() => courts.find((item) => item.id === booking.courtId) ?? null, [booking.courtId, courts]);

  const canConfirm = booking.status === "DRAFT" || booking.status === "CONFIRMED";
  const canCancel = booking.status === "DRAFT" || booking.status === "CONFIRMED";
  const canReschedule = booking.status === "DRAFT" || booking.status === "CONFIRMED";

  async function submitReschedule() {
    if (!startTime || !endTime) {
      return;
    }
    await onAction("reschedule", booking, {
      courtId: courtId || booking.courtId,
      startTime: toIsoWithOffset(startTime),
      endTime: toIsoWithOffset(endTime),
    });
  }

  return (
    <section className="booking-drawer-body">
      <div className="booking-drawer-summary">
        <div>
          <p className="eyebrow">Booking</p>
          <h3>{court?.name ?? "San khong ro"}</h3>
          <code>{booking.id}</code>
        </div>
        <BookingStatusBadge status={booking.status} paymentStatus={booking.paymentStatus} />
      </div>

      <dl className="booking-detail-list">
        <div><dt>Thoi gian</dt><dd>{toLocalDateTime(booking.startTime)} - {toLocalDateTime(booking.endTime)}</dd></div>
        <div><dt>Khach hang</dt><dd><code>{booking.customerId}</code></dd></div>
        <div><dt>Tong tien</dt><dd>{formatCurrency(booking.priceTotal)}</dd></div>
        <div><dt>Dat coc can thu</dt><dd>{formatCurrency(booking.depositRequired)}</dd></div>
        <div><dt>Dat coc da thu</dt><dd>{formatCurrency(booking.depositPaid)}</dd></div>
      </dl>

      <section className="booking-drawer-actions">
        <Button size="sm" onClick={() => onAction("confirm", booking)} disabled={busy || !canConfirm}>
          Xac nhan
        </Button>
        <Button size="sm" variant="danger" onClick={() => onAction("cancel", booking)} disabled={busy || !canCancel}>
          Huy booking
        </Button>
        <Button size="sm" variant="secondary" onClick={() => setRescheduleOpen((value) => !value)} disabled={busy || !canReschedule}>
          Doi lich
        </Button>
        <Button size="sm" variant="ghost" disabled>
          Check-in
        </Button>
        <Button size="sm" variant="ghost" disabled>
          Hoan tat
        </Button>
      </section>
      <p className="muted">Check-in/complete hien do scheduler backend tu cap nhat, chua co API thao tac thu cong.</p>

      {rescheduleOpen ? (
        <section className="booking-reschedule-form">
          <label>San
            <select value={courtId} onChange={(event) => setCourtId(event.target.value)}>
              {courts.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
            </select>
          </label>
          <label>Bat dau
            <input type="datetime-local" value={startTime} onChange={(event) => setStartTime(event.target.value)} />
          </label>
          <label>Ket thuc
            <input type="datetime-local" value={endTime} onChange={(event) => setEndTime(event.target.value)} />
          </label>
          <Button size="sm" onClick={() => { void submitReschedule(); }} disabled={busy || !startTime || !endTime}>
            Luu lich moi
          </Button>
        </section>
      ) : null}
    </section>
  );
}
