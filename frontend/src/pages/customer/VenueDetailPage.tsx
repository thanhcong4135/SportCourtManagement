import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { BookingSummary } from "../../components/booking/BookingSummary";
import { MobileBookingBar } from "../../components/booking/MobileBookingBar";
import { TimeSlotGrid, type SlotStatus } from "../../components/booking/TimeSlotGrid";
import { Button, StatusBadge, useToast } from "../../components/ui";
import { useAuth } from "../../context/AuthContext";
import { defaultVenueAmenities, venueGalleryPlaceholders } from "../../data/mockMedia";
import { toErrorPresentation } from "../../lib/errorPresentation";
import { buildDateRangeIso, listBookings, listCourts, listVenues, type Booking, type Court, type Venue } from "../../lib/coreApi";

const START_MINUTE = 5 * 60;
const END_MINUTE = 24 * 60;
const STEP_MINUTE = 30;

const timeMarkers = Array.from({ length: (END_MINUTE - START_MINUTE) / STEP_MINUTE + 1 }, (_, idx) => {
  const total = START_MINUTE + idx * STEP_MINUTE;
  const hh = String(Math.floor(total / 60)).padStart(2, "0");
  const mm = String(total % 60).padStart(2, "0");
  return `${hh}:${mm}`;
});
const slotMarkers = timeMarkers.slice(0, -1);

type SlotAnchor = { courtId: string; slotIndex: number };
type SelectedRange = { courtId: string; startIndex: number; endIndex: number };

function formatIsoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function getTodayIsoDate(): string {
  return formatIsoDate(new Date());
}

function toMinutes(time: string): number {
  const [hh, mm] = time.split(":").map(Number);
  return hh * 60 + mm;
}

function mapBookingToSlotStatus(booking: Booking): SlotStatus {
  if (booking.status === "DRAFT") {
    return "held";
  }
  if (booking.status === "CONFIRMED" || booking.status === "IN_PROGRESS" || booking.status === "COMPLETED") {
    return "booked";
  }
  return "free";
}

function statusRank(status: SlotStatus): number {
  switch (status) {
    case "booked":
      return 3;
    case "held":
      return 2;
    case "blocked":
      return 1;
    default:
      return 0;
  }
}

export function VenueDetailPage() {
  const { venueId = "" } = useParams<{ venueId: string }>();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const { showToast } = useToast();
  const [searchParams] = useSearchParams();

  const [venue, setVenue] = useState<Venue | null>(null);
  const [courts, setCourts] = useState<Court[]>([]);
  const [bookingsByCourt, setBookingsByCourt] = useState<Record<string, Booking[]>>({});
  const [selectedCourtId, setSelectedCourtId] = useState(searchParams.get("courtId") ?? "");
  const [selectedDate, setSelectedDate] = useState(searchParams.get("date") ?? formatIsoDate(new Date()));
  const [selectionAnchor, setSelectionAnchor] = useState<SlotAnchor | null>(null);
  const [selectedRange, setSelectedRange] = useState<SelectedRange | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | null>(null);

  useEffect(() => {
    async function loadVenue() {
      if (!venueId) {
        return;
      }
      try {
        setError(null);
        setTraceId(null);
        const venues = await listVenues();
        const currentVenue = venues.find((item) => item.id === venueId) ?? null;
        setVenue(currentVenue);
        const venueCourts = currentVenue ? await listCourts(venueId) : [];
        setCourts(venueCourts);
        setSelectedCourtId((current) => (
          current && venueCourts.some((court) => court.id === current)
            ? current
            : (venueCourts[0]?.id ?? "")
        ));
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được thông tin sân");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      }
    }
    void loadVenue();
  }, [venueId]);

  useEffect(() => {
    async function loadSchedule() {
      if (!courts.length) {
        setBookingsByCourt({});
        return;
      }
      try {
        setLoading(true);
        setError(null);
        setTraceId(null);
        const range = buildDateRangeIso(selectedDate);
        const rows = await Promise.all(courts.map(async (court) => {
          const page = await listBookings({
            courtId: court.id,
            from: range.from,
            to: range.to,
            size: 120,
          });
          return [court.id, page.items ?? []] as const;
        }));
        setBookingsByCourt(Object.fromEntries(rows));
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được lịch trống");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      } finally {
        setLoading(false);
      }
    }
    void loadSchedule();
  }, [courts, selectedDate]);

  const slotStatusesByCourt = useMemo(() => {
    const isToday = selectedDate === getTodayIsoDate();
    const now = new Date();
    const nowMinute = now.getHours() * 60 + now.getMinutes();
    const result: Record<string, SlotStatus[]> = {};
    courts.forEach((court) => {
      const slots: SlotStatus[] = Array.from({ length: slotMarkers.length }, () => "free");
      const bookings = bookingsByCourt[court.id] ?? [];
      bookings.forEach((booking) => {
        if (booking.status === "CANCELED" || booking.status === "FAILED_TIMEOUT") {
          return;
        }
        const slotStatus = mapBookingToSlotStatus(booking);
        const start = new Date(booking.startTime);
        const end = new Date(booking.endTime);
        const bookingStart = start.getHours() * 60 + start.getMinutes();
        const bookingEnd = end.getHours() * 60 + end.getMinutes();
        slotMarkers.forEach((marker, index) => {
          const minute = toMinutes(marker);
          if (minute >= bookingStart && minute < bookingEnd && statusRank(slotStatus) > statusRank(slots[index])) {
            slots[index] = slotStatus;
          }
        });
      });

      if (isToday) {
        slotMarkers.forEach((marker, index) => {
          if (toMinutes(marker) <= nowMinute) {
            slots[index] = "blocked";
          }
        });
      }
      result[court.id] = slots;
    });
    return result;
  }, [bookingsByCourt, courts, selectedDate]);

  const selectedCourt = useMemo(
    () => courts.find((court) => court.id === (selectedRange?.courtId || selectedCourtId)) ?? null,
    [courts, selectedCourtId, selectedRange?.courtId],
  );

  const selectedStart = selectedRange ? slotMarkers[selectedRange.startIndex] : "";
  const selectedEnd = selectedRange ? timeMarkers[selectedRange.endIndex] : "";
  const selectedSlotCount = selectedRange ? selectedRange.endIndex - selectedRange.startIndex : 0;
  const selectedHours = selectedSlotCount / 2;

  function getSlotStatus(courtId: string, slotIndex: number): SlotStatus {
    return slotStatusesByCourt[courtId]?.[slotIndex] ?? "free";
  }

  function isRangeFree(courtId: string, startIndex: number, endIndex: number): boolean {
    for (let i = startIndex; i < endIndex; i += 1) {
      if (getSlotStatus(courtId, i) !== "free") {
        return false;
      }
    }
    return true;
  }

  function applyRange(courtId: string, startIndex: number, endIndex: number, keepAnchor: boolean) {
    setSelectedCourtId(courtId);
    setSelectedRange({ courtId, startIndex, endIndex });
    setSelectionAnchor(keepAnchor ? { courtId, slotIndex: startIndex } : null);
  }

  function handleCellClick(courtId: string, slotIndex: number) {
    const status = getSlotStatus(courtId, slotIndex);
    if (status !== "free") {
      showToast({
        title: "Khung giờ không khả dụng",
        message: status === "held" ? "Khung giờ này đang được giữ chỗ." : "Khung giờ này đã có người đặt hoặc đã khóa.",
        variant: "warning",
      });
      return;
    }

    if (!selectionAnchor || selectionAnchor.courtId !== courtId) {
      applyRange(courtId, slotIndex, slotIndex + 1, true);
      return;
    }

    const startIndex = Math.min(selectionAnchor.slotIndex, slotIndex);
    const endIndex = Math.max(selectionAnchor.slotIndex, slotIndex) + 1;
    if (!isRangeFree(courtId, startIndex, endIndex)) {
      showToast({
        title: "Khoảng chọn không hợp lệ",
        message: "Khoảng giờ có ô đã được đặt/giữ, vui lòng chọn lại.",
        variant: "error",
      });
      applyRange(courtId, slotIndex, slotIndex + 1, true);
      return;
    }
    applyRange(courtId, startIndex, endIndex, false);
  }

  function goCheckout() {
    if (!selectedRange) {
      setError("Vui lòng chọn khung giờ trước khi tiếp tục.");
      setTraceId(null);
      return;
    }
    const params = new URLSearchParams({
      venueId,
      courtId: selectedRange.courtId,
      date: selectedDate,
      start: selectedStart,
      end: selectedEnd,
    });
    navigate(`/booking/form?${params.toString()}`);
  }

  if (!venueId) {
    return (
      <div className="alobo-screen venue-detail-screen">
        <p className="inline-error">Thiếu mã cụm sân.</p>
      </div>
    );
  }

  return (
    <div className="alobo-screen venue-detail-screen">
      <header className="simple-topbar">
        <Link to="/discover" className="back-link">←</Link>
        <h1>Đặt lịch ngày trực quan</h1>
        <button type="button" className="icon-btn" onClick={() => navigate(isAuthenticated ? "/account" : "/auth/login")}>
          {isAuthenticated ? "Tài khoản" : "Đăng nhập"}
        </button>
      </header>

      <section className="venue-detail-gallery">
        {venueGalleryPlaceholders.slice(0, 4).map((background, index) => (
          <div key={background} className={`venue-gallery-item ${index === 0 ? "main" : ""}`} style={{ background }} />
        ))}
      </section>

      <section className="venue-detail-info">
        <div>
          <h2>{venue?.name ?? "Đang tải..."}</h2>
          <p>{venue?.address ?? "-"}</p>
          <div className="venue-detail-tags">
            <StatusBadge variant="success" label="Mở cửa 05:00 - 24:00" />
            <StatusBadge variant="neutral" label={`${courts.length} sân`} />
            <StatusBadge variant="warning" label="Cọc tối thiểu 50%" />
          </div>
        </div>
        <div className="venue-amenities">
          {defaultVenueAmenities.map((item) => <span key={item}>{item}</span>)}
        </div>
      </section>

      <section className="venue-detail-pricing">
        <h3>Bảng giá tham khảo</h3>
        <div className="price-grid">
          <article>
            <strong>Giờ thường</strong>
            <p>05:00 - 17:00</p>
            <span>Từ 120.000đ/h</span>
          </article>
          <article>
            <strong>Giờ cao điểm</strong>
            <p>17:00 - 23:00</p>
            <span>Từ 160.000đ/h</span>
          </article>
          <article>
            <strong>Cuối tuần</strong>
            <p>Thứ 7 - Chủ nhật</p>
            <span>Từ 140.000đ/h</span>
          </article>
        </div>
      </section>

      <section className="venue-detail-toolbar">
        <label>
          Ngày đặt
          <input type="date" value={selectedDate} onChange={(event) => setSelectedDate(event.target.value)} />
        </label>
        <label>
          Sân con
          <select value={selectedCourtId} onChange={(event) => setSelectedCourtId(event.target.value)}>
            {courts.map((court) => (
              <option key={court.id} value={court.id}>{court.name}</option>
            ))}
          </select>
        </label>
        <Button variant="secondary" onClick={() => navigate(`/booking/grid?venueId=${venueId}&courtId=${selectedCourtId}`)}>
          Chuyển sang bảng chọn nâng cao
        </Button>
      </section>

      <section className="booking-grid-legend">
        <div className="legend-chip"><span className="legend-color free" />Trống</div>
        <div className="legend-chip"><span className="legend-color held" />Đang giữ</div>
        <div className="legend-chip"><span className="legend-color booked" />Đã đặt</div>
        <div className="legend-chip"><span className="legend-color blocked" />Đã khóa</div>
      </section>

      {error && <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p>}
      {loading && <p className="inline-muted">Đang tải lịch trống...</p>}

      <div className="venue-detail-content">
        <TimeSlotGrid
          courts={courts.map((court) => ({ id: court.id, name: court.name }))}
          slotMarkers={slotMarkers}
          gridEndMarkers={timeMarkers.slice(1)}
          selectedCourtId={selectedCourtId}
          selectedRange={selectedRange}
          selectionAnchor={selectionAnchor}
          getSlotStatus={getSlotStatus}
          onSelectCourt={setSelectedCourtId}
          onClickCell={handleCellClick}
        />

        <aside className="venue-detail-sticky-summary">
          <BookingSummary
            title={selectedRange ? `${selectedStart} - ${selectedEnd}` : "Chưa chọn khung giờ"}
            subtitle={selectedCourt ? `${selectedCourt.name} · ${selectedDate}` : "Chọn sân và khung giờ"}
            duration={selectedRange ? `${selectedHours.toFixed(1)} giờ` : "-"}
            note={selectedRange ? `${selectedSlotCount} slot (30 phút/slot)` : "Chưa có dữ liệu"}
            ctaLabel="Tiếp tục đặt sân"
            onNext={goCheckout}
            disabled={!selectedRange}
          />
        </aside>
      </div>

      <MobileBookingBar
        label="Khung giờ đã chọn"
        value={selectedRange ? `${selectedStart} - ${selectedEnd}` : "Chưa chọn"}
        ctaLabel="Tiếp tục"
        onClick={goCheckout}
        disabled={!selectedRange}
      />
    </div>
  );
}
