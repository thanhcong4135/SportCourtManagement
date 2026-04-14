import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { formatCurrency } from "../../lib/api";
import { toErrorPresentation } from "../../lib/errorPresentation";
import { type Court, type Venue, listCourts, listVenues } from "../../lib/coreApi";
import { useAuth } from "../../context/AuthContext";
import { BottomNavigation } from "../../components/BottomNavigation";

type DiscoverCard = {
  venue: Venue;
  court: Court;
  rating: number;
  distanceKm: number;
  openingHours: string;
};

const demoBanners = [
  "linear-gradient(132deg, #67cfd7 0%, #2f8cb6 52%, #2f586f 100%)",
  "linear-gradient(132deg, #8dc87f 0%, #42996c 52%, #1f6c57 100%)",
  "linear-gradient(132deg, #8db5ff 0%, #4c6cd4 52%, #22266a 100%)",
  "linear-gradient(132deg, #ffd579 0%, #eca34f 55%, #ab5f29 100%)",
];

const defaultHours = "05:00 - 24:00";

function randomBySeed(seed: string, min: number, max: number) {
  const acc = Array.from(seed).reduce((sum, char) => sum + char.charCodeAt(0), 0);
  const normalized = (acc % 1000) / 1000;
  return min + normalized * (max - min);
}

function todayLabel() {
  return new Intl.DateTimeFormat("vi-VN", {
    weekday: "long",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(new Date());
}

export function DiscoverPage() {
  const navigate = useNavigate();
  const { token, isAuthenticated } = useAuth();
  const [venues, setVenues] = useState<Venue[]>([]);
  const [courtsByVenue, setCourtsByVenue] = useState<Record<string, Court[]>>({});
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | null>(null);

  useEffect(() => {
    async function load() {
      try {
        setLoading(true);
        setError(null);
        setTraceId(null);
        const venueRows = await listVenues();
        setVenues(venueRows);

        const courtRows = await Promise.all(
          venueRows.map(async (venue) => {
            const courts = await listCourts(venue.id);
            return [venue.id, courts] as const;
          }),
        );

        setCourtsByVenue(Object.fromEntries(courtRows));
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được dữ liệu sân");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      } finally {
        setLoading(false);
      }
    }

    void load();
  }, []);

  const cards = useMemo<DiscoverCard[]>(() => {
    const allCards = venues.flatMap((venue) => {
      const venueCourts = courtsByVenue[venue.id] ?? [];
      return venueCourts.map((court) => ({
        venue,
        court,
        rating: Number(randomBySeed(court.id, 4.2, 5.0).toFixed(1)),
        distanceKm: Number(randomBySeed(`${court.id}-dist`, 1.3, 12.8).toFixed(1)),
        openingHours: defaultHours,
      }));
    });

    const lowered = search.trim().toLowerCase();
    if (!lowered) {
      return allCards;
    }
    return allCards.filter((card) => {
      return (
        card.venue.name.toLowerCase().includes(lowered)
        || card.venue.address.toLowerCase().includes(lowered)
        || card.court.name.toLowerCase().includes(lowered)
      );
    });
  }, [courtsByVenue, search, venues]);

  function openBooking(card: DiscoverCard) {
    const params = new URLSearchParams({
      venueId: card.venue.id,
      courtId: card.court.id,
    });
    navigate(`/booking/grid?${params.toString()}`);
  }

  return (
    <div className="alobo-screen discover-screen">
      <div className="discover-sticky">
        <header className="discover-hero">
          <div className="discover-hero-row">
            <div className="discover-avatar">SC</div>
            <div>
              <p className="discover-date">{todayLabel()}</p>
              <h1>{isAuthenticated ? token?.email ?? "SportCourt" : "Khách"}</h1>
            </div>
            <div className="discover-hero-actions">
              {!isAuthenticated ? (
                <Link to="/auth/login" className="pill-link">
                  Đăng nhập
                </Link>
              ) : (
                <Link to="/account" className="pill-link">
                  Tài khoản
                </Link>
              )}
            </div>
          </div>
        </header>

        <section className="discover-toolbar">
          <input
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Tìm kiếm sân, địa chỉ"
          />
          <button type="button">Bản đồ</button>
          <button type="button">Sân đã đặt</button>
        </section>
      </div>

      {error && <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p>}
      {loading && <p className="inline-muted">Đang tải danh sách sân...</p>}

      <section className="discover-grid">
        {cards.map((card, index) => (
          <article className="discover-card" key={`${card.venue.id}-${card.court.id}`}>
            <div className="discover-card-banner" style={{ background: demoBanners[index % demoBanners.length] }}>
              <div className="discover-badges">
                <span className="badge rating">★ {card.rating}</span>
                <span className="badge quick">Đơn ngày</span>
                <span className="badge event">Sự kiện</span>
              </div>
            </div>
            <div className="discover-card-body">
              <div>
                <h3>{card.venue.name}</h3>
                <p className="distance">({card.distanceKm}km) {card.venue.address}</p>
                <p className="muted">{card.court.name} · {card.court.sportType}</p>
                <p className="muted">{card.openingHours}</p>
              </div>
              <button type="button" className="booking-cta" onClick={() => openBooking(card)}>
                ĐẶT LỊCH
              </button>
            </div>
          </article>
        ))}

        {!loading && cards.length === 0 && <p className="inline-muted">Không tìm thấy sân phù hợp.</p>}
      </section>

      <section className="discover-summary">
        <article>
          <p>Tổng sân hiện có</p>
          <strong>{cards.length}</strong>
        </article>
        <article>
          <p>Gợi ý đặt cọc</p>
          <strong>{formatCurrency(50000)}</strong>
        </article>
      </section>

      <BottomNavigation active="discover" />
    </div>
  );
}
