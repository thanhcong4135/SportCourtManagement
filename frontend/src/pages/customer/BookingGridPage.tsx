import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { BookingSummary } from "../../components/booking/BookingSummary";
import { TimeSlotGrid, type SlotStatus } from "../../components/booking/TimeSlotGrid";
import { Button, Modal, useToast } from "../../components/ui";
import { toErrorPresentation } from "../../lib/errorPresentation";
import {
  buildDateRangeIso,
  listAvailabilitySchedule,
  listCourts,
  listVenues,
  type AvailabilityBlock,
  type Court,
  type Venue,
} from "../../lib/coreApi";

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

function formatIsoDate(value: Date) {
  const y = value.getFullYear();
  const m = String(value.getMonth() + 1).padStart(2, "0");
  const d = String(value.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function getTodayIsoDate(): string {
  return formatIsoDate(new Date());
}

function toMinutes(time: string) {
  const [hh, mm] = time.split(":").map(Number);
  return hh * 60 + mm;
}

function mapAvailabilityToSlotStatus(block: AvailabilityBlock): SlotStatus {
  return block.status === "HELD" ? "held" : "booked";
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

export function BookingGridPage() {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();

  const [venues, setVenues] = useState<Venue[]>([]);
  const [courts, setCourts] = useState<Court[]>([]);
  const [availabilityByCourt, setAvailabilityByCourt] = useState<Record<string, AvailabilityBlock[]>>({});

  const [selectedVenueId, setSelectedVenueId] = useState(searchParams.get("venueId") ?? "");
  const [selectedCourtId, setSelectedCourtId] = useState(searchParams.get("courtId") ?? "");
  const [selectedDate, setSelectedDate] = useState(searchParams.get("date") ?? formatIsoDate(new Date()));
  const [selectionAnchor, setSelectionAnchor] = useState<SlotAnchor | null>(null);
  const [selectedRange, setSelectedRange] = useState<SelectedRange | null>(null);
  const [showClearSelectionModal, setShowClearSelectionModal] = useState(false);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | null>(null);

  useEffect(() => {
    async function loadVenueRows() {
      try {
        setError(null);
        setTraceId(null);
        const rows = await listVenues();
        setVenues(rows);
        if (!selectedVenueId && rows[0]) {
          setSelectedVenueId(rows[0].id);
        }
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được cụm sân");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      }
    }
    void loadVenueRows();
  }, [selectedVenueId]);

  useEffect(() => {
    async function loadCourtRows() {
      if (!selectedVenueId) {
        setCourts([]);
        return;
      }
      try {
        setError(null);
        setTraceId(null);
        const rows = await listCourts(selectedVenueId);
        setCourts(rows);
        setSelectedCourtId((current) => (
          current && rows.some((court) => court.id === current) ? current : (rows[0]?.id ?? "")
        ));
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được danh sách sân con");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      }
    }
    void loadCourtRows();
  }, [selectedVenueId]);

  useEffect(() => {
    async function loadSchedule() {
      if (!courts.length) {
        setAvailabilityByCourt({});
        return;
      }
      try {
        setLoading(true);
        setError(null);
        setTraceId(null);
        const range = buildDateRangeIso(selectedDate);
        const blocks = await listAvailabilitySchedule(courts.map((court) => court.id), range.from, range.to);
        const grouped = Object.fromEntries(courts.map((court) => [court.id, [] as AvailabilityBlock[]]));
        blocks.forEach((block) => {
          grouped[block.courtId]?.push(block);
        });
        setAvailabilityByCourt(grouped);
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được lịch đặt");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      } finally {
        setLoading(false);
      }
    }
    void loadSchedule();
  }, [courts, selectedDate]);

  useEffect(() => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (selectedVenueId) {
        next.set("venueId", selectedVenueId);
      }
      if (selectedCourtId) {
        next.set("courtId", selectedCourtId);
      }
      next.set("date", selectedDate);
      return next;
    }, { replace: true });
  }, [selectedCourtId, selectedDate, selectedVenueId, setSearchParams]);

  const selectedVenue = useMemo(() => venues.find((venue) => venue.id === selectedVenueId) ?? null, [selectedVenueId, venues]);
  const selectedCourt = useMemo(() => courts.find((court) => court.id === selectedCourtId) ?? null, [courts, selectedCourtId]);

  const slotStatusesByCourt = useMemo(() => {
    const isToday = selectedDate === getTodayIsoDate();
    const now = new Date();
    const nowMinute = now.getHours() * 60 + now.getMinutes();
    const map: Record<string, SlotStatus[]> = {};
    courts.forEach((court) => {
      const slots: SlotStatus[] = Array.from({ length: slotMarkers.length }, () => "free");
      const blocks = availabilityByCourt[court.id] ?? [];
      blocks.forEach((block) => {
        const status = mapAvailabilityToSlotStatus(block);
        const start = new Date(block.startTime);
        const end = new Date(block.endTime);
        const bookingStart = start.getHours() * 60 + start.getMinutes();
        const bookingEnd = end.getHours() * 60 + end.getMinutes();
        slotMarkers.forEach((marker, index) => {
          const minute = toMinutes(marker);
          if (minute >= bookingStart && minute < bookingEnd && statusRank(status) > statusRank(slots[index])) {
            slots[index] = status;
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
      map[court.id] = slots;
    });
    return map;
  }, [availabilityByCourt, courts, selectedDate]);

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
        message: status === "held" ? "Khung giờ này đang được giữ chỗ." : "Khung giờ này đã đặt hoặc đã khóa.",
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
        message: "Khoảng giờ có ô không trống, vui lòng chọn lại.",
        variant: "error",
      });
      applyRange(courtId, slotIndex, slotIndex + 1, true);
      return;
    }
    applyRange(courtId, startIndex, endIndex, false);
  }

  function goNext() {
    if (!selectedRange || !selectedCourt) {
      setError("Vui lòng chọn sân và khung giờ trước khi tiếp tục.");
      setTraceId(null);
      return;
    }
    const params = new URLSearchParams({
      venueId: selectedVenueId,
      courtId: selectedRange.courtId,
      date: selectedDate,
      start: selectedStart,
      end: selectedEnd,
    });
    navigate(`/booking/form?${params.toString()}`);
  }

  function resetSelection() {
    setSelectionAnchor(null);
    setSelectedRange(null);
    setShowClearSelectionModal(false);
  }

  return (
    <div className="alobo-screen booking-grid-screen">
      <header className="simple-topbar">
        <Link to={`/venues/${selectedVenueId}`} className="back-link">←</Link>
        <h1>Đặt lịch ngày trực quan</h1>
        <div className="booking-grid-date-chip">{selectedDate}</div>
      </header>

      <section className="booking-grid-toolbar booking-grid-toolbar-compact">
        <label>
          Cụm sân
          <select value={selectedVenueId} onChange={(event) => setSelectedVenueId(event.target.value)}>
            {venues.map((venue) => (
              <option key={venue.id} value={venue.id}>{venue.name}</option>
            ))}
          </select>
        </label>

        <label>
          Ngày
          <input type="date" value={selectedDate} onChange={(event) => setSelectedDate(event.target.value)} />
        </label>

        <label>
          Sân ưu tiên
          <select value={selectedCourtId} onChange={(event) => setSelectedCourtId(event.target.value)}>
            {courts.map((court) => (
              <option key={court.id} value={court.id}>{court.name}</option>
            ))}
          </select>
        </label>

        <div className="booking-quick-dates">
          <Button variant="ghost" size="sm" onClick={() => setSelectedDate(formatIsoDate(new Date()))}>Hôm nay</Button>
          <Button variant="secondary" size="sm" onClick={() => navigate(`/venues/${selectedVenueId}?courtId=${selectedCourtId}&date=${selectedDate}`)}>
            Quay lại trang sân
          </Button>
        </div>
      </section>

      <section className="booking-grid-legend">
        <div className="legend-chip"><span className="legend-color free" />Trống</div>
        <div className="legend-chip"><span className="legend-color held" />Giữ chỗ</div>
        <div className="legend-chip"><span className="legend-color booked" />Đã đặt</div>
        <div className="legend-chip"><span className="legend-color blocked" />Đã khóa</div>
        <div className="legend-chip"><span className="legend-color selected" />Đang chọn</div>
      </section>

      <section className="booking-grid-notice">
        <p>
          <strong>Lưu ý:</strong> Chọn 1 ô để bắt đầu, chọn thêm 1 ô cùng hàng để kết thúc.
          Hệ thống chỉ cho phép chọn ô trống.
        </p>
        {selectedVenue ? <p>{selectedVenue.name} · {selectedVenue.address}</p> : null}
      </section>

      {error && <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p>}
      {loading && <p className="inline-muted">Đang đồng bộ lịch đặt...</p>}

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

      <BookingSummary
        title={selectedRange ? `${selectedStart} - ${selectedEnd}` : "Chưa chọn khung giờ"}
        subtitle={selectedCourt ? `${selectedCourt.name} · ${selectedDate}` : "Chọn sân và chạm trên bảng thời gian"}
        duration={selectedRange ? `${selectedHours.toFixed(1)} giờ` : "-"}
        note={selectedRange ? `${selectedSlotCount} slot (30 phút/slot)` : "Chưa có dữ liệu"}
        ctaLabel="Tiếp tục đặt sân"
        onNext={goNext}
        disabled={!selectedRange}
      />

      <Modal
        open={showClearSelectionModal}
        title="Xóa khung giờ đã chọn?"
        message="Bạn sẽ cần chọn lại từ đầu trên bảng thời gian."
        confirmLabel="Xóa"
        cancelLabel="Giữ lại"
        onCancel={() => setShowClearSelectionModal(false)}
        onConfirm={resetSelection}
      />
    </div>
  );
}
