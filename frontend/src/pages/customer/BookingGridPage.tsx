import { type CSSProperties, useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Button, Modal, useToast } from "../../components/ui";
import { useAuth } from "../../context/AuthContext";
import { trackEvent } from "../../lib/analytics";
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

function formatIsoDate(value: Date) {
  const y = value.getFullYear();
  const m = String(value.getMonth() + 1).padStart(2, "0");
  const d = String(value.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function toMinutes(time: string) {
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

export function BookingGridPage() {
  const navigate = useNavigate();
  const { token } = useAuth();
  const { showToast } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();

  const [venues, setVenues] = useState<Venue[]>([]);
  const [courts, setCourts] = useState<Court[]>([]);
  const [bookingsByCourt, setBookingsByCourt] = useState<Record<string, Booking[]>>({});

  const [selectedVenueId, setSelectedVenueId] = useState(searchParams.get("venueId") ?? "");
  const [selectedCourtId, setSelectedCourtId] = useState(searchParams.get("courtId") ?? "");
  const [selectedDate, setSelectedDate] = useState(() => formatIsoDate(new Date()));
  const [startTime, setStartTime] = useState(searchParams.get("start") ?? "");
  const [endTime, setEndTime] = useState(searchParams.get("end") ?? "");

  const [selectionAnchor, setSelectionAnchor] = useState<SlotAnchor | null>(null);
  const [selectedRange, setSelectedRange] = useState<SelectedRange | null>(null);
  const [refreshSignal, setRefreshSignal] = useState(0);
  const [showClearSelectionModal, setShowClearSelectionModal] = useState(false);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | null>(null);

  useEffect(() => {
    async function loadVenues() {
      try {
        setError(null);
        setTraceId(null);
        const venueRows = await listVenues();
        setVenues(venueRows);

        if (!venueRows.length) {
          setSelectedVenueId("");
          return;
        }

        if (!selectedVenueId || !venueRows.some((venue) => venue.id === selectedVenueId)) {
          setSelectedVenueId(venueRows[0].id);
        }
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được cụm sân");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      }
    }

    void loadVenues();
  }, [selectedVenueId]);

  useEffect(() => {
    async function loadCourtsByVenue() {
      if (!selectedVenueId) {
        setCourts([]);
        return;
      }

      try {
        setError(null);
        setTraceId(null);
        const rows = await listCourts(selectedVenueId);
        setCourts(rows);
        setSelectedCourtId((prev) => (
          prev && rows.some((court) => court.id === prev) ? prev : rows[0]?.id ?? ""
        ));
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được sân");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      }
    }

    void loadCourtsByVenue();
  }, [selectedVenueId]);

  useEffect(() => {
    setSelectionAnchor(null);
    setSelectedRange(null);
    setStartTime("");
    setEndTime("");
  }, [selectedVenueId, selectedDate]);

  useEffect(() => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (selectedVenueId) {
        next.set("venueId", selectedVenueId);
      } else {
        next.delete("venueId");
      }
      if (selectedCourtId) {
        next.set("courtId", selectedCourtId);
      } else {
        next.delete("courtId");
      }
      return next;
    });
  }, [selectedCourtId, selectedVenueId, setSearchParams]);

  useEffect(() => {
    async function loadSchedule() {
      if (!courts.length || !token?.accessToken) {
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
              size: 80,
            });
            return [court.id, page.items ?? []] as const;
          }),
        );

        setBookingsByCourt(Object.fromEntries(rows));
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được lịch đặt sân");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      } finally {
        setLoading(false);
      }
    }

    void loadSchedule();
  }, [courts, refreshSignal, selectedDate, token?.accessToken]);

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

  const selectedVenue = useMemo(() => venues.find((venue) => venue.id === selectedVenueId) ?? null, [selectedVenueId, venues]);
  const selectedCourt = useMemo(() => courts.find((court) => court.id === selectedCourtId) ?? null, [courts, selectedCourtId]);

  const slotStatusesByCourt = useMemo(() => {
    const courtSlotMap: Record<string, SlotStatus[]> = {};

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

      courtSlotMap[court.id] = slots;
    });

    return courtSlotMap;
  }, [bookingsByCourt, courts]);

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
    setStartTime(slotMarkers[startIndex]);
    setEndTime(timeMarkers[endIndex]);
    setSelectionAnchor(keepAnchor ? { courtId, slotIndex: startIndex } : null);
  }

  function handleCellClick(courtId: string, slotIndex: number) {
    const status = getSlotStatus(courtId, slotIndex);
    if (status !== "free") {
      showToast({
        title: "Không chọn được ô này",
        message: status === "held"
          ? "Khung giờ này đang được giữ tạm bởi khách khác."
          : "Khung giờ đã có người đặt hoặc bị khóa.",
        variant: "warning",
      });
      return;
    }

    setError(null);
    setTraceId(null);

    if (!selectionAnchor || selectionAnchor.courtId !== courtId) {
      applyRange(courtId, slotIndex, slotIndex + 1, true);
      showToast({
        title: "Đã chọn điểm bắt đầu",
        message: `${slotMarkers[slotIndex]} · ${courts.find((item) => item.id === courtId)?.name ?? "Sân"}`,
      });
      return;
    }

    const startIndex = Math.min(selectionAnchor.slotIndex, slotIndex);
    const endIndex = Math.max(selectionAnchor.slotIndex, slotIndex) + 1;

    if (!isRangeFree(courtId, startIndex, endIndex)) {
      setError("Khung giờ chọn có ô đã được đặt hoặc bị khóa. Vui lòng chọn lại.");
      showToast({ title: "Khung giờ không hợp lệ", message: "Khoảng thời gian có ô không trống.", variant: "error" });
      applyRange(courtId, slotIndex, slotIndex + 1, true);
      return;
    }

    applyRange(courtId, startIndex, endIndex, false);
    trackEvent("funnel_grid_slot_range_selected", {
      venueId: selectedVenueId,
      courtId,
      date: selectedDate,
      start: slotMarkers[startIndex],
      end: timeMarkers[endIndex],
      slotCount: endIndex - startIndex,
    });
    showToast({
      title: "Đã chọn khung giờ",
      message: `${slotMarkers[startIndex]} - ${timeMarkers[endIndex]}`,
      variant: "success",
    });
  }

  function goNext() {
    if (!selectedCourt || !selectedDate || !startTime || !endTime) {
      setError("Vui lòng chọn sân và khung giờ trên bảng trước khi tiếp tục.");
      setTraceId(null);
      showToast({
        title: "Thiếu thông tin đặt lịch",
        message: "Bạn cần chọn sân và khung giờ trước khi tiếp tục.",
        variant: "warning",
      });
      return;
    }

    if (toMinutes(endTime) <= toMinutes(startTime)) {
      setError("Khung giờ không hợp lệ: giờ kết thúc phải lớn hơn giờ bắt đầu.");
      setTraceId(null);
      showToast({ title: "Giờ không hợp lệ", message: "Giờ kết thúc phải lớn hơn giờ bắt đầu.", variant: "error" });
      return;
    }

    const params = new URLSearchParams({
      venueId: selectedVenueId,
      courtId: selectedCourt.id,
      date: selectedDate,
      start: startTime,
      end: endTime,
    });
    trackEvent("funnel_grid_continue_checkout", {
      venueId: selectedVenueId,
      courtId: selectedCourt.id,
      date: selectedDate,
      start: startTime,
      end: endTime,
    });
    navigate(`/booking/form?${params.toString()}`);
  }

  function resetSelection() {
    trackEvent("funnel_grid_selection_cleared", {
      venueId: selectedVenueId,
      courtId: selectedCourtId,
      date: selectedDate,
    });
    setSelectionAnchor(null);
    setSelectedRange(null);
    setStartTime("");
    setEndTime("");
    setShowClearSelectionModal(false);
    showToast({ title: "Đã xóa lựa chọn", variant: "info" });
  }

  function applyQuickDate(offsetDays: number) {
    const next = new Date();
    next.setDate(next.getDate() + offsetDays);
    setSelectedDate(formatIsoDate(next));
  }

  const timelineStyle = useMemo(() => ({
    "--slot-count": slotMarkers.length,
  }) as CSSProperties, []);

  return (
    <div className="alobo-screen booking-grid-screen">
      <header className="simple-topbar">
        <Link to="/discover" className="back-link">←</Link>
        <h1>Đặt lịch ngày trực quan</h1>
        <div className="booking-grid-date-chip">{selectedDate}</div>
      </header>

      <section className="booking-grid-toolbar booking-grid-toolbar-compact">
        <label>
          Cụm sân
          <select value={selectedVenueId} onChange={(event) => setSelectedVenueId(event.target.value)}>
            {venues.map((venue) => (
              <option key={venue.id} value={venue.id}>
                {venue.name}
              </option>
            ))}
          </select>
        </label>

        <label>
          Ngày
          <input type="date" value={selectedDate} onChange={(event) => setSelectedDate(event.target.value)} />
        </label>

        <div className="booking-quick-dates">
          <Button variant="ghost" size="sm" onClick={() => applyQuickDate(0)}>Hôm nay</Button>
          <Button variant="ghost" size="sm" onClick={() => applyQuickDate(1)}>Ngày mai</Button>
          <Button variant="secondary" size="sm" onClick={() => setRefreshSignal((prev) => prev + 1)}>Làm mới</Button>
        </div>

        <div className="booking-selected-slot" aria-live="polite">
          <span>Khung giờ đã chọn</span>
          <strong>{startTime && endTime ? `${startTime} - ${endTime}` : "Chưa chọn"}</strong>
          {selectedCourt && <small>Sân: {selectedCourt.name}</small>}
        </div>
      </section>

      <section className="booking-grid-legend">
        <div className="legend-chip"><span className="legend-color free" />Trống</div>
        <div className="legend-chip"><span className="legend-color held" />Đang giữ chỗ</div>
        <div className="legend-chip"><span className="legend-color booked" />Đã đặt</div>
        <div className="legend-chip"><span className="legend-color blocked" />Khóa</div>
        <div className="legend-chip"><span className="legend-color selected" />Đang chọn</div>
        <button type="button" className="legend-link" onClick={() => showToast({ title: "Bảng giá", message: "Bảng giá chi tiết sẽ được mở trong phase admin pricing.", variant: "info" })}>
          Xem sân &amp; bảng giá
        </button>
      </section>

      <section className="booking-grid-notice">
        <p>
          <strong>Lưu ý:</strong> Nhấn 1 ô để chọn điểm bắt đầu, nhấn thêm 1 ô cùng hàng để chọn điểm kết thúc.
          Chỉ chọn được các ô trống.
        </p>
        {selectedVenue && <p>{selectedVenue.name} · {selectedVenue.address} · Tổng {courts.length} sân</p>}
        <p>Dữ liệu được tự làm mới mỗi 15 giây để giảm lệch trạng thái khi nhiều người đặt cùng lúc.</p>
      </section>

      {error && <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p>}
      {loading && <p className="inline-muted">Đang đồng bộ lịch đặt...</p>}

      <section className="timeline-wrap booking-timeline-wrap" aria-label="Booking timeline" style={timelineStyle}>
        <div className="timeline-header">
          <div className="court-col-head">Sân</div>
          {slotMarkers.map((marker) => (
            <div key={marker} className="time-col-head">{marker}</div>
          ))}
        </div>

        {courts.map((court) => (
          <div className={`timeline-row ${selectedCourtId === court.id ? "is-active" : ""}`} key={court.id}>
            <button
              type="button"
              className="court-col timeline-court-button"
              onClick={() => setSelectedCourtId(court.id)}
              title={court.name}
            >
              {court.name}
            </button>

            {slotMarkers.map((marker, slotIndex) => {
              const slotStatus = getSlotStatus(court.id, slotIndex);
              const isSelected = selectedRange
                && selectedRange.courtId === court.id
                && slotIndex >= selectedRange.startIndex
                && slotIndex < selectedRange.endIndex;
              const isAnchor = selectionAnchor?.courtId === court.id && selectionAnchor.slotIndex === slotIndex;

              return (
                <button
                  type="button"
                  key={`${court.id}-${marker}`}
                  className={`time-cell-grid cell-${slotStatus}${isSelected ? " cell-selected" : ""}${isAnchor ? " cell-anchor" : ""}`}
                  onClick={() => handleCellClick(court.id, slotIndex)}
                  disabled={slotStatus !== "free"}
                  title={`${court.name} · ${marker}`}
                  aria-label={`${court.name} ${marker}`}
                />
              );
            })}
          </div>
        ))}
      </section>

      <section className="booking-grid-summary-card">
        <div>
          <p>Tóm tắt chọn giờ</p>
          <strong>{startTime && endTime ? `${startTime} - ${endTime}` : "Chưa chọn khung giờ"}</strong>
          <small>{selectedCourt ? `${selectedCourt.name} · ${selectedDate}` : "Chưa chọn sân"}</small>
        </div>
        <div className="booking-grid-summary-actions">
          <Button variant="ghost" onClick={() => setShowClearSelectionModal(true)} disabled={!startTime || !endTime}>
            Xóa lựa chọn
          </Button>
          <Button variant="primary" onClick={goNext}>
            Tiếp tục checkout
          </Button>
        </div>
      </section>

      <button type="button" className="primary-bottom-btn" onClick={goNext}>
        TIẾP THEO
      </button>

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
