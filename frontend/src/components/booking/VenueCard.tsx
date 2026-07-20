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
  bannerImageUrl?: string | null;
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
  bannerImageUrl,
  onBook,
  actionLabel = "Dat san",
}: VenueCardProps) {
  return (
    <article className="venue-card">
      <div
        className="venue-card-banner"
        style={bannerImageUrl ? { backgroundImage: `url("${bannerImageUrl}")` } : { background: bannerStyle }}
      >
        <div className="venue-card-badges">
          <span className="badge rating">* {rating}</span>
          <span className="badge quick">Don ngay</span>
          <span className="badge event">Su kien</span>
        </div>
        <span className="venue-card-distance-badge">{distanceKm}km</span>

        <div className="venue-card-body">
          <div className="venue-card-main">
            <h3>{venueName}</h3>
            <p className="distance">{address}</p>
            <p className="muted">
              {courtName} - {sportType}
            </p>
            <p className="muted">{openingHours}</p>
            <div className="venue-card-meta">
              <StatusBadge variant={availabilityVariant} label={availabilityLabel} />
              <strong>{priceLabel}</strong>
            </div>
            <div className="venue-card-footer-row">
              <div className="venue-card-amenities">
                {amenities.slice(0, 3).map((item) => (
                  <span key={item}>{item}</span>
                ))}
              </div>
              <Button variant="primary" className="booking-cta" onClick={onBook}>
                {actionLabel}
              </Button>
            </div>
          </div>
        </div>
      </div>
    </article>
  );
}
