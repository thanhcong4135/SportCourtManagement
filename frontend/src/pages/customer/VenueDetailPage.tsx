import { type CSSProperties, useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { BottomNavigation } from "../../components/BottomNavigation";
import { Button, StatusBadge, useToast } from "../../components/ui";
import { useAuth } from "../../context/AuthContext";
import { toErrorPresentation } from "../../lib/errorPresentation";
import {
  type Booking,
  type Court,
  buildDateRangeIso,
  listBookings,
  listCourts,
  listVenues,
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

type SlotStatus = "free" | "held" | "booked" | "blocked";

type SlotAnchor = {
  courtId: string;
  slotIndex: number;
};

type SelectedRange = {
  courtId: string;
  startIndex: number;
  endIndex: number;
};

function formatIsoDate(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function toMinutes(time: string): number {
  const [hh, mm] = time.split(":").map(Number);
  return hh * 60 + mm;
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

function mapBookingToSlotStatus(booking: Booking): SlotStatus {
  if (booking.status === "DRAFT") {
    return "held";
  }
  if (booking.status === "CONFIRMED" || booking.status === "IN_PROGRESS" || booking.status === "COMPLETED") {
    return "booked";
  }
  return "free";
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
  const [error, setError] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [refreshSignal, setRefreshSignal] = useState(0);

  useEffect(() => {
    async function loadVenueAndCourts() {
      if (!venueId) {
        return;
      }
      try {
        setError(null);
        setTraceId(null);
        const venueRows = await listVenues();
        const matchedVenue = venueRows.find((row) => row.id === venueId) ?? null;
        setVenue(matchedVenue);

        if (!matchedVenue) {
          setCourts([]);
          return;
        }

        const courtRows = await listCourts(venueId);
        setCourts(courtRows);
        setSelectedCourtId((current) => (
          current && courtRows.some((court) => court.id === current)
            ? current
            : (courtRows[0]?.id ?? "")
        ));
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được thông tin sân");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      }
    }

    void loadVenueAndCourts();
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
        const rows = await Promise.all(
          courts.map(async (court) => {
            const page = await listBookings({
              courtId: court.id,
              from: range.from,
              to: range.to,
              size: 120,
            });
            return [court.id, page.items ?? []] as const;
          }),
        );

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
  }, [courts, refreshSignal, selectedDate]);

  useEffect(() => {
    setSelectionAnchor(null);
    setSelectedRange(null);
  }, [selectedDate, selectedCourtId, venueId]);

  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState === "visible") {
        setRefreshSignal((prev) => prev + 1);
      }
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => document.removeEventListener("visibilitychange", onVisible);
  }, []);

  useEffect(() => {
    if (!courts.length) {
      return;
    }
    const handle = window.setInterval(() => {
      setRefreshSignal((prev) => prev + 1);
    }, 15_000);
    return () => window.clearInterval(handle);
  }, [courts.length]);

  const slotStatusesByCourt = useMemo(() => {
    const result: Record<string, SlotStatus[]> = {};

    courts.forEach((court) => {
      const slots: SlotStatus[] = Array.from({ length: slotMarkers.length }, () => "free");
      const rowBookings = bookingsByCourt[court.id] ?? [];

      rowBookings.forEach((booking) => {
        if (booking.status === "CANCELED" || booking.status === "FAILED_TIMEOUT") {
          return;
        }
        const slotStatus = mapBookingToSlotStatus(booking);
        if (slotStatus === "free") {
          return;
        }
        const start = new Date(booking.startTime);
        const end = new Date(booking.endTime);
        const bookingStart = start.getHours() * 60 + start.getMinutes();
        const bookingEnd = end.getHours() * 60 + end.getMinutes();

        slotMarkers.forEach((marker, index) => {
          const markerMinute = toMinutes(marker);
          if (markerMinute >= bookingStart && markerMinute < bookingEnd) {
            if (statusRank(slotStatus) > statusRank(slots[index])) {
              slots[index] = slotStatus;
            }
          }
        });
      });

      result[court.id] = slots;
    });

    return result;
  }, [bookingsByCourt, courts]);

  const selectedCourt = useMemo(
    () => courts.find((court) => court.id === selectedRange?.courtId || court.id === selectedCourtId) ?? null,
    [courts, selectedCourtId, selectedRange?.courtId],
  );

  const selectedStart = selectedRange ? slotMarkers[selectedRange.startIndex] : "";
  const selectedEnd = selectedRange ? timeMarkers[selectedRange.endIndex] : "";
  const selectedSlotCount = selectedRange ? selectedRange.endIndex - selectedRange.startIndex : 0;
  const selectedHours = selectedSlotCount / 2;

  const timelineStyle = useMemo(() => ({
    "--slot-count": slotMarkers.length,
  }) as CSSProperties, []);

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
        message: status === "held" ? "Khung giờ này đang được giữ chỗ." : "Khung giờ này đã có người đặt.",
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
        message: "Khoảng giờ có ô đã được đặt/giữ. Vui lòng chọn lại.",
        variant: "error",
      });
      applyRange(courtId, slotIndex, slotIndex + 1, true);
      return;
    }
    applyRange(courtId, startIndex, endIndex, false);
  }

  function goCheckout() {
    if (!selectedRange) {
      setError("Vui lòng chọn khung giờ trên bảng trước khi tiếp tục.");
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
        <h1>Chi tiết sân & lịch trống</h1>
        <button type="button" className="icon-btn" onClick={() => navigate(isAuthenticated ? "/account" : "/auth/login")}>
          {isAuthenticated ? "Tài khoản" : "Đăng nhập"}
        </button>
      </header>

      <section className="venue-detail-hero">
        <div className="venue-detail-cover" />
        <div className="venue-detail-meta">
          <h2>{venue?.name ?? "Đang tải..."}</h2>
          <p>{venue?.address ?? "-"}</p>
          <div className="venue-detail-tags">
            <StatusBadge variant="success" label="Mở cửa 05:00 - 24:00" />
            <StatusBadge variant="neutral" label={`${courts.length} sân`} />
            <StatusBadge variant="warning" label="Cọc tối thiểu 50%" />
          </div>
          <div className="venue-detail-policy">
            <p><strong>Tiện ích:</strong> Bãi xe, phòng chờ, nước uống, thay đồ.</p>
            <p><strong>Chính sách:</strong> Hủy miễn phí trước giờ chơi theo điều kiện của chủ sân.</p>
            <p><strong>Bản đồ:</strong> Có thể mở vị trí sân trực tiếp trong phase sau.</p>
          </div>
        </div>
      </section>

      <section className="booking-grid-toolbar venue-detail-toolbar">
        <label>
          Ngay choi
          <input type="date" value={selectedDate} onChange={(event) => setSelectedDate(event.target.value)} />
        </label>
        <label>
          San uu tien
          <select value={selectedCourtId} onChange={(event) => setSelectedCourtId(event.target.value)}>
            {courts.map((court) => (
              <option key={court.id} value={court.id}>{court.name}</option>
            ))}
          </select>
        </label>
        <div className="venue-detail-actions">
          <Button variant="ghost" size="sm" onClick={() => setSelectedDate(formatIsoDate(new Date()))}>
            Hôm nay
          </Button>
          <Button variant="secondary" onClick={() => setRefreshSignal((prev) => prev + 1)}>
            Làm mới lịch trống
          </Button>
          <Button variant="ghost" onClick={() => navigate(`/booking/grid?venueId=${venueId}&courtId=${selectedCourtId}`)}>
            Chuyển sang bảng đặt nâng cao
          </Button>
        </div>
      </section>

      <section className="booking-grid-legend">
        <div className="legend-chip"><span className="legend-color free" />Trống</div>
        <div className="legend-chip"><span className="legend-color held" />Đang giữ chỗ</div>
        <div className="legend-chip"><span className="legend-color booked" />Đã đặt</div>
        <div className="legend-chip"><span className="legend-color blocked" />Khóa</div>
        <div className="legend-chip">Tự động đồng bộ mỗi 15 giây</div>
      </section>

      {error && <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p>}
      {loading && <p className="inline-muted">Đang tải lịch trống...</p>}

      <section className="timeline-wrap booking-timeline-wrap venue-detail-timeline" style={timelineStyle}>
        <div className="timeline-header">
          <div className="court-col-head">Sân</div>
          {slotMarkers.map((marker) => (
            <div key={marker} className="time-col-head">{marker}</div>
          ))}
        </div>

        {courts.map((court) => (
          <div className={`timeline-row ${selectedRange?.courtId === court.id ? "is-active" : ""}`} key={court.id}>
            <button
              type="button"
              className="court-col timeline-court-button"
              onClick={() => setSelectedCourtId(court.id)}
              title={court.name}
            >
              {court.name}
            </button>
            {slotMarkers.map((marker, slotIndex) => {
              const status = getSlotStatus(court.id, slotIndex);
              const isSelected = selectedRange
                && selectedRange.courtId === court.id
                && slotIndex >= selectedRange.startIndex
                && slotIndex < selectedRange.endIndex;
              const isAnchor = selectionAnchor?.courtId === court.id && selectionAnchor.slotIndex === slotIndex;
              return (
                <button
                  type="button"
                  key={`${court.id}-${marker}`}
                  className={`time-cell-grid cell-${status}${isSelected ? " cell-selected" : ""}${isAnchor ? " cell-anchor" : ""}`}
                  onClick={() => handleCellClick(court.id, slotIndex)}
                  disabled={status !== "free"}
                  title={`${court.name} · ${marker}`}
                />
              );
            })}
          </div>
        ))}
      </section>

      <section className="venue-detail-summary">
        <div>
          <p>Đã chọn khung giờ</p>
          <strong>{selectedRange ? `${selectedStart} - ${selectedEnd}` : "Chưa chọn"}</strong>
          <small>
            {selectedCourt ? `${selectedCourt.name} · ${selectedDate}` : "Chọn sân và chạm trên bảng thời gian"}
          </small>
        </div>
        <div>
          <p>Tổng thời lượng</p>
          <strong>{selectedRange ? `${selectedHours.toFixed(1)} giờ` : "-"}</strong>
          <small>{selectedRange ? `${selectedSlotCount} slot (30 phút/slot)` : "Chưa có dữ liệu"}</small>
        </div>
        <Button variant="primary" onClick={goCheckout} disabled={!selectedRange}>
          Tiếp tục thanh toán
        </Button>
      </section>

      <BottomNavigation active="discover" />
    </div>
  );
}


