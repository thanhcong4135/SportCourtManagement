import { type CSSProperties } from "react";

export type SlotStatus = "free" | "held" | "booked" | "blocked";

type CourtRow = {
  id: string;
  name: string;
};

type TimeSlotGridProps = {
  courts: CourtRow[];
  slotMarkers: string[];
  gridEndMarkers: string[];
  selectedCourtId: string;
  selectedRange?: { courtId: string; startIndex: number; endIndex: number } | null;
  selectionAnchor?: { courtId: string; slotIndex: number } | null;
  getSlotStatus: (courtId: string, slotIndex: number) => SlotStatus;
  onSelectCourt: (courtId: string) => void;
  onClickCell: (courtId: string, slotIndex: number) => void;
};

function formatTimelineMarker(marker: string): string {
  const [hour, minute] = marker.split(":");
  if (!hour || !minute) {
    return marker;
  }
  return `${Number(hour)}:${minute}`;
}

export function TimeSlotGrid({
  courts,
  slotMarkers,
  gridEndMarkers,
  selectedCourtId,
  selectedRange,
  selectionAnchor,
  getSlotStatus,
  onSelectCourt,
  onClickCell,
}: TimeSlotGridProps) {
  const timelineStyle = { "--slot-count": slotMarkers.length } as CSSProperties;

  return (
    <section className="timeline-wrap booking-timeline-wrap" style={timelineStyle}>
      <div className="timeline-header">
        <div className="court-col-head">Sân</div>
        {slotMarkers.map((marker, slotIndex) => (
          <div key={marker} className={`time-col-head${slotIndex === 0 ? " is-first" : ""}`}>
            <span>{formatTimelineMarker(marker)}</span>
          </div>
        ))}
      </div>

      {courts.map((court) => (
        <div className={`timeline-row ${selectedCourtId === court.id ? "is-active" : ""}`} key={court.id}>
          <button
            type="button"
            className="court-col timeline-court-button"
            onClick={() => onSelectCourt(court.id)}
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
                onClick={() => onClickCell(court.id, slotIndex)}
                disabled={slotStatus !== "free"}
                title={`${court.name} · ${formatTimelineMarker(marker)} - ${formatTimelineMarker(gridEndMarkers[slotIndex])}`}
                aria-label={`${court.name} ${formatTimelineMarker(marker)}`}
              />
            );
          })}
        </div>
      ))}
    </section>
  );
}
