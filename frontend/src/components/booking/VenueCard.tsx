import { Button, StatusBadge } from "../ui";

type VenueCardProps = {
  venueName: string;
  courtName: string;
  sportType: string;
  address: string;
  distanceKm: number;
  rating: number;
  openingHours: string;
  priceLabel: string;
  availabilityLabel: string;
  availabilityVariant: "success" | "danger" | "neutral" | "warning";
  amenities: string[];
  bannerStyle: string;
  onBook: () => void;
  actionLabel?: string;
};

export function VenueCard({
  venueName,
  courtName,
  sportType,
  address,
  distanceKm,
  rating,
  openingHours,
  priceLabel,
  availabilityLabel,
  availabilityVariant,
  amenities,
  bannerStyle,
  onBook,
  actionLabel = "Đặt sân",
}: VenueCardProps) {
  return (
    <article className="venue-card">
      <div className="venue-card-banner" style={{ background: bannerStyle }}>
        <div className="venue-card-badges">
          <span className="badge rating">★ {rating}</span>
          <span className="badge quick">Đơn ngày</span>
          <span className="badge event">Sự kiện</span>
        </div>
      </div>
      <div className="venue-card-body">
        <div className="venue-card-main">
          <h3>{venueName}</h3>
          <p className="distance">({distanceKm}km) {address}</p>
          <p className="muted">{courtName} · {sportType}</p>
          <p className="muted">{openingHours}</p>
          <div className="venue-card-meta">
            <StatusBadge variant={availabilityVariant} label={availabilityLabel} />
            <strong>{priceLabel}</strong>
          </div>
          <div className="venue-card-amenities">
            {amenities.slice(0, 3).map((item) => <span key={item}>{item}</span>)}
          </div>
        </div>
        <Button variant="primary" className="booking-cta" onClick={onBook}>
          {actionLabel}
        </Button>
      </div>
    </article>
  );
}

