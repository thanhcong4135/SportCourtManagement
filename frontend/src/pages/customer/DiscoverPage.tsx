import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { BottomNavigation } from "../../components/BottomNavigation";
import { VenueCard } from "../../components/booking/VenueCard";
import { VenueFilter } from "../../components/booking/VenueFilter";
import { EmptyState, ErrorState, SkeletonCard } from "../../components/ui";
import { useAuth } from "../../context/AuthContext";
import { defaultVenueAmenities, venueGalleryPlaceholders } from "../../data/mockMedia";
import { useDiscoverData } from "../../features/venues/useDiscoverData";
import { useDebouncedValue } from "../../hooks/useDebouncedValue";
import { formatCurrency } from "../../lib/api";
import { buildOffsetIso, checkAvailability, quoteBooking } from "../../lib/coreApi";
import { toErrorPresentation } from "../../lib/errorPresentation";

type SortValue = "POPULAR" | "NEAREST" | "PRICE_LOW";
type PriceValue = "ALL" | "UNDER_100K" | "FROM_100K_TO_150K" | "ABOVE_150K";

type DiscoverCard = {
  venueId: string;
  venueName: string;
  venueAddress: string;
  courtId: string;
  courtName: string;
  sportType: string;
  rating: number;
  distanceKm: number;
};

type CardInsight = {
  availability: boolean | null;
  quote: number | null;
};

const sortOptions = [
  { label: "Phổ biến", value: "POPULAR" },
  { label: "Gần nhất", value: "NEAREST" },
  { label: "Giá thấp", value: "PRICE_LOW" },
];

const priceOptions = [
  { label: "Mọi mức giá", value: "ALL" },
  { label: "< 100.000đ", value: "UNDER_100K" },
  { label: "100.000đ - 150.000đ", value: "FROM_100K_TO_150K" },
  { label: "> 150.000đ", value: "ABOVE_150K" },
];

const sportOptions = [
  { label: "Tất cả môn", value: "ALL" },
  { label: "Pickleball", value: "PICKLEBALL" },
  { label: "Cầu lông", value: "BADMINTON" },
  { label: "Tennis", value: "TENNIS" },
  { label: "Bóng đá", value: "FOOTBALL" },
];

function normalizeSort(value: string | null): SortValue {
  if (value === "NEAREST" || value === "PRICE_LOW") {
    return value;
  }
  return "POPULAR";
}

function normalizePrice(value: string | null): PriceValue {
  if (value === "UNDER_100K" || value === "FROM_100K_TO_150K" || value === "ABOVE_150K") {
    return value;
  }
  return "ALL";
}

function getTodayIsoDate() {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, "0");
  const d = String(now.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function add30Minutes(hhmm: string) {
  const [hour, minute] = hhmm.split(":").map(Number);
  const total = hour * 60 + minute + 30;
  const nextHour = String(Math.floor(total / 60)).padStart(2, "0");
  const nextMinute = String(total % 60).padStart(2, "0");
  return `${nextHour}:${nextMinute}`;
}

function createDistance(seed: string): number {
  const sum = Array.from(seed).reduce((acc, ch) => acc + ch.charCodeAt(0), 0);
  return Number(((sum % 120) / 10 + 1.2).toFixed(1));
}

function createRating(seed: string): number {
  const sum = Array.from(seed).reduce((acc, ch) => acc + ch.charCodeAt(0), 0);
  return Number((4.2 + ((sum % 8) * 0.1)).toFixed(1));
}

function matchPriceFilter(quote: number | null, filter: PriceValue): boolean {
  if (filter === "ALL") {
    return true;
  }
  if (quote === null) {
    return false;
  }
  if (filter === "UNDER_100K") {
    return quote < 100_000;
  }
  if (filter === "FROM_100K_TO_150K") {
    return quote >= 100_000 && quote <= 150_000;
  }
  return quote > 150_000;
}

export function DiscoverPage() {
  const navigate = useNavigate();
  const { token, isAuthenticated } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  const [searchInput, setSearchInput] = useState(searchParams.get("q") ?? "");
  const debouncedSearch = useDebouncedValue(searchInput, 250);

  const sortValue = normalizeSort(searchParams.get("sort"));
  const sportFilter = searchParams.get("sport") ?? "ALL";
  const areaFilter = searchParams.get("area") ?? "ALL";
  const priceFilter = normalizePrice(searchParams.get("price"));
  const selectedDate = searchParams.get("date") ?? getTodayIsoDate();
  const selectedTime = searchParams.get("time") ?? "18:00";

  const discoverQuery = useDiscoverData();
  const errorUi = discoverQuery.error ? toErrorPresentation(discoverQuery.error, "Không tải được danh sách sân") : null;

  const [insights, setInsights] = useState<Record<string, CardInsight>>({});

  useEffect(() => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (debouncedSearch.trim()) {
        next.set("q", debouncedSearch.trim());
      } else {
        next.delete("q");
      }
      return next;
    }, { replace: true });
  }, [debouncedSearch, setSearchParams]);

  const areaOptions = useMemo(() => {
    const rows = discoverQuery.data?.venues ?? [];
    return [{ label: "Tất cả khu vực", value: "ALL" }, ...rows.map((venue) => ({ label: venue.address, value: venue.id }))];
  }, [discoverQuery.data?.venues]);

  const cards = useMemo(() => {
    const venues = discoverQuery.data?.venues ?? [];
    const courtsByVenue = discoverQuery.data?.courtsByVenue ?? {};
    const keyword = (searchParams.get("q") ?? "").toLowerCase();

    const mapped: DiscoverCard[] = venues.flatMap((venue) => (courtsByVenue[venue.id] ?? []).map((court) => ({
      venueId: venue.id,
      venueName: venue.name,
      venueAddress: venue.address,
      courtId: court.id,
      courtName: court.name,
      sportType: court.sportType,
      rating: createRating(court.id),
      distanceKm: createDistance(court.id),
    })));

    const filtered = mapped.filter((card) => {
      const matchKeyword = !keyword
        || card.venueName.toLowerCase().includes(keyword)
        || card.venueAddress.toLowerCase().includes(keyword)
        || card.courtName.toLowerCase().includes(keyword);
      const matchSport = sportFilter === "ALL" || card.sportType.toUpperCase() === sportFilter;
      const matchArea = areaFilter === "ALL" || card.venueId === areaFilter;
      const quote = insights[card.courtId]?.quote ?? null;
      const matchPrice = matchPriceFilter(quote, priceFilter);
      return matchKeyword && matchSport && matchArea && matchPrice;
    });

    return filtered.sort((left, right) => {
      if (sortValue === "NEAREST") {
        return left.distanceKm - right.distanceKm;
      }
      if (sortValue === "PRICE_LOW") {
        const leftPrice = insights[left.courtId]?.quote ?? Number.MAX_SAFE_INTEGER;
        const rightPrice = insights[right.courtId]?.quote ?? Number.MAX_SAFE_INTEGER;
        return leftPrice - rightPrice;
      }
      return right.rating - left.rating;
    });
  }, [areaFilter, discoverQuery.data?.courtsByVenue, discoverQuery.data?.venues, insights, priceFilter, searchParams, sortValue, sportFilter]);

  useEffect(() => {
    if (!cards.length) {
      return;
    }
    let canceled = false;
    const startIso = buildOffsetIso(selectedDate, selectedTime);
    const endIso = buildOffsetIso(selectedDate, add30Minutes(selectedTime));

    async function loadInsights() {
      const rows = await Promise.all(cards.slice(0, 24).map(async (card) => {
        const availability = await checkAvailability(card.courtId, startIso, endIso)
          .then((response) => response.available)
          .catch(() => null);
        const quote = isAuthenticated
          ? await quoteBooking(card.courtId, startIso, endIso).then((response) => response.totalPrice).catch(() => null)
          : null;
        return [card.courtId, { availability, quote }] as const;
      }));

      if (canceled) {
        return;
      }
      setInsights((prev) => ({ ...prev, ...Object.fromEntries(rows) }));
    }

    void loadInsights();
    return () => {
      canceled = true;
    };
  }, [cards, isAuthenticated, selectedDate, selectedTime]);

  const pendingCount = cards.filter((card) => insights[card.courtId]?.availability === true).length;

  const quickDateLabel = useMemo(() => (
    new Intl.DateTimeFormat("vi-VN", { weekday: "long", day: "2-digit", month: "2-digit", year: "numeric" }).format(new Date())
  ), []);

  function updateFilter(key: "sport" | "area" | "price" | "sort", value: string) {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if ((key === "sport" || key === "area" || key === "price") && value === "ALL") {
        next.delete(key);
      } else if (key === "sort" && value === "POPULAR") {
        next.delete(key);
      } else {
        next.set(key, value);
      }
      return next;
    }, { replace: true });
  }

  function clearFilters() {
    setSearchInput("");
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      next.delete("q");
      next.delete("sport");
      next.delete("area");
      next.delete("price");
      next.delete("sort");
      return next;
    }, { replace: true });
  }

  return (
    <div className="alobo-screen discover-screen">
      <header className="discover-hero">
        <div className="discover-hero-row">
          <div className="discover-avatar">SC</div>
          <div>
            <p className="discover-date">{quickDateLabel}</p>
            <h1>{isAuthenticated ? token?.email ?? "SportCourt" : "Khách đặt sân"}</h1>
          </div>
          <div className="discover-hero-actions">
            <Link to={isAuthenticated ? "/account" : "/auth/login"} className="pill-link">
              {isAuthenticated ? "Tài khoản" : "Đăng nhập"}
            </Link>
          </div>
        </div>
      </header>

      <VenueFilter
        keyword={searchInput}
        sport={sportFilter}
        area={areaFilter}
        date={selectedDate}
        price={priceFilter}
        sort={sortValue}
        sportOptions={sportOptions}
        areaOptions={areaOptions}
        priceOptions={priceOptions}
        sortOptions={sortOptions}
        onKeywordChange={setSearchInput}
        onSportChange={(value) => updateFilter("sport", value)}
        onAreaChange={(value) => updateFilter("area", value)}
        onDateChange={(value) => setSearchParams((curr) => {
          const next = new URLSearchParams(curr);
          next.set("date", value);
          return next;
        }, { replace: true })}
        onPriceChange={(value) => updateFilter("price", value)}
        onSortChange={(value) => updateFilter("sort", value)}
        onClear={clearFilters}
      />

      {errorUi ? (
        <ErrorState message={errorUi.message} traceId={errorUi.traceId} onRetry={() => void discoverQuery.refetch()} />
      ) : null}

      <section className="discover-results-head">
        <div>
          <h2>Danh sách sân</h2>
          <p>{cards.length} sân phù hợp · {pendingCount} sân còn slot theo giờ đã chọn</p>
        </div>
      </section>

      <section className="discover-grid">
        {discoverQuery.isLoading ? <SkeletonCard count={6} /> : null}
        {!discoverQuery.isLoading && cards.map((card, index) => {
          const insight = insights[card.courtId] ?? { availability: null, quote: null };
          const availabilityLabel = insight.availability === true ? "Còn slot" : insight.availability === false ? "Đã kín" : "Đang kiểm tra";
          const availabilityVariant = insight.availability === true ? "success" : insight.availability === false ? "danger" : "neutral";
          const priceLabel = insight.quote !== null ? `Từ ${formatCurrency(insight.quote)}` : "Chưa có bảng giá";

          return (
            <VenueCard
              key={`${card.venueId}-${card.courtId}`}
              venueName={card.venueName}
              courtName={card.courtName}
              sportType={card.sportType}
              address={card.venueAddress}
              distanceKm={card.distanceKm}
              rating={card.rating}
              openingHours="05:00 - 24:00"
              priceLabel={priceLabel}
              availabilityLabel={availabilityLabel}
              availabilityVariant={availabilityVariant}
              amenities={defaultVenueAmenities}
              bannerStyle={venueGalleryPlaceholders[index % venueGalleryPlaceholders.length]}
              onBook={() => navigate(`/venues/${card.venueId}?courtId=${card.courtId}&date=${selectedDate}`)}
              actionLabel="Đặt sân"
            />
          );
        })}
      </section>

      {!discoverQuery.isLoading && cards.length === 0 ? (
        <EmptyState title="Không tìm thấy sân phù hợp" description="Thử điều chỉnh bộ lọc hoặc từ khóa tìm kiếm." />
      ) : null}

      <BottomNavigation active="discover" />
    </div>
  );
}
