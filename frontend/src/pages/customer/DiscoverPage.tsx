import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { BottomNavigation } from "../../components/BottomNavigation";
import {
  Button,
  Drawer,
  EmptyState,
  ErrorState,
  SelectField,
  SkeletonCard,
  StatusBadge,
  Tabs,
  useToast,
} from "../../components/ui";
import { useAuth } from "../../context/AuthContext";
import { useDiscoverData } from "../../features/venues/useDiscoverData";
import { useDebouncedValue } from "../../hooks/useDebouncedValue";
import { trackEvent } from "../../lib/analytics";
import { formatCurrency } from "../../lib/api";
import {
  type Court,
  type Venue,
  buildOffsetIso,
  checkAvailability,
  quoteBooking,
} from "../../lib/coreApi";
import { toErrorPresentation } from "../../lib/errorPresentation";

type SortValue = "POPULAR" | "NEAREST" | "PRICE_LOW" | "AVAILABILITY";
type PriceValue = "ALL" | "UNDER_100K" | "FROM_100K_TO_150K" | "ABOVE_150K";

type DiscoverCard = {
  venue: Venue;
  court: Court;
  rating: number;
  distanceKm: number;
  openingHours: string;
};

type CardInsight = {
  availability: boolean | null;
  quote: number | null;
};

const demoBanners = [
  "linear-gradient(132deg, #67cfd7 0%, #2f8cb6 52%, #2f586f 100%)",
  "linear-gradient(132deg, #8dc87f 0%, #42996c 52%, #1f6c57 100%)",
  "linear-gradient(132deg, #8db5ff 0%, #4c6cd4 52%, #22266a 100%)",
  "linear-gradient(132deg, #ffd579 0%, #eca34f 55%, #ab5f29 100%)",
];

const sortOptions = [
  { label: "Phổ biến", value: "POPULAR" },
  { label: "Gần nhất", value: "NEAREST" },
  { label: "Giá thấp", value: "PRICE_LOW" },
  { label: "Còn nhiều slot", value: "AVAILABILITY" },
];

const priceOptions = [
  { label: "Mọi mức giá", value: "ALL" },
  { label: "< 100.000đ", value: "UNDER_100K" },
  { label: "100.000đ - 150.000đ", value: "FROM_100K_TO_150K" },
  { label: "> 150.000đ", value: "ABOVE_150K" },
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

function normalizeSort(value: string | null): SortValue {
  const allowed: SortValue[] = ["POPULAR", "NEAREST", "PRICE_LOW", "AVAILABILITY"];
  if (value && allowed.includes(value as SortValue)) {
    return value as SortValue;
  }
  return "POPULAR";
}

function normalizePrice(value: string | null): PriceValue {
  const allowed: PriceValue[] = ["ALL", "UNDER_100K", "FROM_100K_TO_150K", "ABOVE_150K"];
  if (value && allowed.includes(value as PriceValue)) {
    return value as PriceValue;
  }
  return "ALL";
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

function toDiscoverCards(venues: Venue[], courtsByVenue: Record<string, Court[]>): DiscoverCard[] {
  return venues.flatMap((venue) => {
    const venueCourts = courtsByVenue[venue.id] ?? [];
    return venueCourts.map((court) => ({
      venue,
      court,
      rating: Number(randomBySeed(court.id, 4.2, 5.0).toFixed(1)),
      distanceKm: Number(randomBySeed(`${court.id}-dist`, 1.3, 12.8).toFixed(1)),
      openingHours: defaultHours,
    }));
  });
}

export function DiscoverPage() {
  const navigate = useNavigate();
  const { token, isAuthenticated } = useAuth();
  const { showToast } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const [searchInput, setSearchInput] = useState(searchParams.get("q") ?? "");
  const [isFilterDrawerOpen, setFilterDrawerOpen] = useState(false);
  const [visibleCount, setVisibleCount] = useState(18);
  const [cardInsights, setCardInsights] = useState<Record<string, CardInsight>>({});
  const debouncedSearch = useDebouncedValue(searchInput, 350);

  const sportFilter = searchParams.get("sport") ?? "ALL";
  const sortValue = normalizeSort(searchParams.get("sort"));
  const priceFilter = normalizePrice(searchParams.get("price"));
  const keyword = (searchParams.get("q") ?? "").trim().toLowerCase();
  const selectedDate = searchParams.get("date") ?? getTodayIsoDate();
  const selectedTime = searchParams.get("time") ?? "18:00";
  const discoverQuery = useDiscoverData();
  const errorUi = discoverQuery.error ? toErrorPresentation(discoverQuery.error, "Không tải được dữ liệu sân") : null;

  useEffect(() => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      const normalized = debouncedSearch.trim();
      if (normalized) {
        next.set("q", normalized);
      } else {
        next.delete("q");
      }
      return next;
    }, { replace: true });
  }, [debouncedSearch, setSearchParams]);

  const sportOptions = useMemo(() => {
    const mapped = new Set<string>();
    Object.values(discoverQuery.data?.courtsByVenue ?? {}).forEach((courts) => {
      courts.forEach((court) => mapped.add(court.sportType.toUpperCase()));
    });
    return [
      { label: "Tất cả môn", value: "ALL" },
      ...Array.from(mapped).sort().map((sport) => ({ label: sport, value: sport })),
    ];
  }, [discoverQuery.data?.courtsByVenue]);

  const cards = useMemo(() => {
    const source = toDiscoverCards(discoverQuery.data?.venues ?? [], discoverQuery.data?.courtsByVenue ?? {});

    const filtered = source.filter((card) => {
      const matchSport = sportFilter === "ALL" || card.court.sportType.toUpperCase() === sportFilter;
      const quote = cardInsights[card.court.id]?.quote ?? null;
      const matchPrice = matchPriceFilter(quote, priceFilter);
      if (!matchSport || !matchPrice) {
        return false;
      }
      if (!keyword) {
        return true;
      }
      return (
        card.venue.name.toLowerCase().includes(keyword)
        || card.venue.address.toLowerCase().includes(keyword)
        || card.court.name.toLowerCase().includes(keyword)
      );
    });

    return filtered.sort((left, right) => {
      switch (sortValue) {
        case "NEAREST":
          return left.distanceKm - right.distanceKm;
        case "PRICE_LOW": {
          const leftPrice = cardInsights[left.court.id]?.quote ?? Number.MAX_SAFE_INTEGER;
          const rightPrice = cardInsights[right.court.id]?.quote ?? Number.MAX_SAFE_INTEGER;
          if (leftPrice !== rightPrice) {
            return leftPrice - rightPrice;
          }
          return right.rating - left.rating;
        }
        case "AVAILABILITY": {
          const leftAvailability = cardInsights[left.court.id]?.availability;
          const rightAvailability = cardInsights[right.court.id]?.availability;
          const toRank = (value: boolean | null | undefined) => {
            if (value === true) {
              return 0;
            }
            if (value === null || value === undefined) {
              return 1;
            }
            return 2;
          };
          const rank = toRank(leftAvailability) - toRank(rightAvailability);
          if (rank !== 0) {
            return rank;
          }
          return right.rating - left.rating;
        }
        default:
          return right.rating - left.rating;
      }
    });
  }, [cardInsights, discoverQuery.data?.courtsByVenue, discoverQuery.data?.venues, keyword, sortValue, sportFilter, priceFilter]);

  const visibleCards = useMemo(() => cards.slice(0, visibleCount), [cards, visibleCount]);

  useEffect(() => {
    if (!visibleCards.length) {
      return;
    }

    let cancelled = false;

    async function loadCardInsights() {
      const startIso = buildOffsetIso(selectedDate, selectedTime);
      const endIso = buildOffsetIso(selectedDate, add30Minutes(selectedTime));
      const entries = await Promise.all(
        visibleCards.map(async (card) => {
          const availabilityPromise = checkAvailability(card.court.id, startIso, endIso)
            .then((response) => response.available)
            .catch(() => null);
          const quotePromise = isAuthenticated
            ? quoteBooking(card.court.id, startIso, endIso)
              .then((response) => response.totalPrice)
              .catch(() => null)
            : Promise.resolve(null);
          const [availability, quote] = await Promise.all([availabilityPromise, quotePromise]);
          return [card.court.id, { availability, quote }] as const;
        }),
      );

      if (cancelled) {
        return;
      }

      setCardInsights((previous) => ({
        ...previous,
        ...Object.fromEntries(entries),
      }));
    }

    void loadCardInsights();

    return () => {
      cancelled = true;
    };
  }, [visibleCards, selectedDate, selectedTime, isAuthenticated]);

  const activeFilterCount = useMemo(() => {
    let count = 0;
    if (keyword) {
      count += 1;
    }
    if (sportFilter !== "ALL") {
      count += 1;
    }
    if (priceFilter !== "ALL") {
      count += 1;
    }
    if (sortValue !== "POPULAR") {
      count += 1;
    }
    return count;
  }, [keyword, sportFilter, priceFilter, sortValue]);

  const suggestedDeposit = useMemo(() => {
    const firstQuoted = cards
      .map((card) => cardInsights[card.court.id]?.quote ?? null)
      .find((value): value is number => typeof value === "number");
    if (firstQuoted === undefined) {
      return null;
    }
    return Math.ceil((firstQuoted * 0.5) / 1000) * 1000;
  }, [cards, cardInsights]);

  function openBooking(card: DiscoverCard) {
    const insight = cardInsights[card.court.id];
    trackEvent("funnel_discover_book_click", {
      venueId: card.venue.id,
      courtId: card.court.id,
      sportType: card.court.sportType,
      priceFrom: insight?.quote ?? null,
      distanceKm: card.distanceKm,
      availability: insight?.availability,
    });
    const params = new URLSearchParams({
      courtId: card.court.id,
      date: selectedDate,
    });
    navigate(`/venues/${card.venue.id}?${params.toString()}`);
  }

  function updateFilter(key: "sport" | "sort" | "price", value: string) {
    setVisibleCount(18);
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (key === "sort" && value === "POPULAR") {
        next.delete("sort");
      } else if ((key === "sport" || key === "price") && value === "ALL") {
        next.delete(key);
      } else {
        next.set(key, value);
      }
      return next;
    }, { replace: true });
  }

  function clearFilters() {
    setVisibleCount(18);
    trackEvent("funnel_discover_clear_filters", {
      hadKeyword: Boolean(searchInput.trim()),
      sportFilter,
      priceFilter,
      sortValue,
    });
    setSearchInput("");
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      next.delete("q");
      next.delete("sport");
      next.delete("price");
      next.delete("sort");
      return next;
    }, { replace: true });
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

        <section className="discover-toolbar discover-toolbar--grid">
          <input
            value={searchInput}
            onChange={(event) => {
              setVisibleCount(18);
              setSearchInput(event.target.value);
            }}
            placeholder="Tìm kiếm sân, địa chỉ"
          />
          <div className="discover-toolbar-filters">
            <SelectField
              className="discover-toolbar-select"
              options={sportOptions}
              value={sportFilter}
              onChange={(event) => updateFilter("sport", event.target.value)}
            />
            <SelectField
              className="discover-toolbar-select"
              options={priceOptions}
              value={priceFilter}
              onChange={(event) => updateFilter("price", event.target.value)}
            />
            <SelectField
              className="discover-toolbar-select"
              options={sortOptions}
              value={sortValue}
              onChange={(event) => updateFilter("sort", event.target.value)}
            />
          </div>
          <Button
            className="discover-toolbar-btn discover-toolbar-btn-mobile"
            variant="ghost"
            onClick={() => setFilterDrawerOpen(true)}
          >
            Bộ lọc
          </Button>
          <Button
            className="discover-toolbar-btn"
            variant="secondary"
            onClick={() => {
              trackEvent("funnel_discover_map_click");
              showToast({ title: "Bản đồ", message: "Tính năng bản đồ sẽ được bật ở phase sau.", variant: "info" });
            }}
          >
            Bản đồ
          </Button>
          <Button
            className="discover-toolbar-btn"
            variant="secondary"
            onClick={() => navigate("/account")}
          >
            Sân đã đặt
          </Button>
        </section>

        <div className="discover-filter-summary">
          <span>
            {activeFilterCount > 0 ? `Đang áp dụng ${activeFilterCount} bộ lọc` : "Chưa áp dụng bộ lọc"}
          </span>
          {activeFilterCount > 0 ? (
            <Button variant="ghost" size="sm" onClick={clearFilters}>
              Xóa tất cả
            </Button>
          ) : null}
        </div>
      </div>

      {errorUi ? (
        <ErrorState
          message={errorUi.message}
          traceId={errorUi.traceId}
          onRetry={() => void discoverQuery.refetch()}
        />
      ) : null}

      <section className="discover-results-head">
        <div>
          <h2>Danh sách sân nổi bật</h2>
          <p>{cards.length} sân phù hợp với bộ lọc hiện tại</p>
        </div>
        <Button
          variant="ghost"
          size="sm"
          onClick={() => showToast({ title: "Sắp mở", message: "Bộ sưu tập ưu đãi sẽ được bật ở phase sau.", variant: "info" })}
        >
          Ưu đãi hôm nay
        </Button>
      </section>

      <section className="discover-grid">
        {discoverQuery.isLoading ? <SkeletonCard count={6} /> : null}

        {!discoverQuery.isLoading && !errorUi ? visibleCards.map((card, index) => {
          const insight = cardInsights[card.court.id] ?? { availability: null, quote: null };
          const availabilityLabel = insight.availability === true
            ? "Còn chỗ"
            : insight.availability === false
              ? "Đã kín"
              : "Đang cập nhật";
          const availabilityVariant = insight.availability === true
            ? "success"
            : insight.availability === false
              ? "danger"
              : "neutral";
          const priceLabel = insight.quote !== null
            ? `Từ ${formatCurrency(insight.quote)}`
            : isAuthenticated
              ? "Chưa có bảng giá"
              : "Đăng nhập để xem giá";
          return (
            <article
              className="discover-card"
              key={`${card.venue.id}-${card.court.id}`}
              data-venue-id={card.venue.id}
              data-court-id={card.court.id}
            >
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
                  <p className="muted">Khung giờ kiểm tra: {selectedDate} {selectedTime}</p>
                  <div className="discover-card-meta">
                    <StatusBadge
                      variant={availabilityVariant}
                      label={availabilityLabel}
                    />
                    <span className="discover-card-price">{priceLabel}</span>
                  </div>
                </div>
                <button
                  type="button"
                  className="booking-cta"
                  data-venue-id={card.venue.id}
                  data-court-id={card.court.id}
                  onClick={() => openBooking(card)}
                >
                  XEM LỊCH TRỐNG
                </button>
              </div>
            </article>
          );
        }) : null}
      </section>

      {!discoverQuery.isLoading && !errorUi && cards.length > visibleCards.length ? (
        <div className="discover-load-more">
          <Button variant="ghost" onClick={() => setVisibleCount((current) => current + 18)}>
            Xem thêm ({cards.length - visibleCards.length} sân)
          </Button>
        </div>
      ) : null}

      {!discoverQuery.isLoading && !errorUi && cards.length === 0 ? (
        <EmptyState
          title="Không tìm thấy sân phù hợp"
          description="Thử xóa từ khóa hoặc đổi bộ lọc để xem thêm kết quả."
        />
      ) : null}

      <section className="discover-summary">
        <article>
          <p>Tổng sân hiện có</p>
          <strong>{cards.length}</strong>
        </article>
        <article>
          <p>Gợi ý đặt cọc</p>
          <strong>{suggestedDeposit !== null ? formatCurrency(suggestedDeposit) : "Đăng nhập để xem"}</strong>
        </article>
      </section>

      <BottomNavigation active="discover" />

      <Drawer open={isFilterDrawerOpen} onClose={() => setFilterDrawerOpen(false)} title="Bộ lọc tìm sân">
        <div className="discover-drawer-filters">
          <SelectField
            label="Môn thể thao"
            options={sportOptions}
            value={sportFilter}
            onChange={(event) => updateFilter("sport", event.target.value)}
          />
          <SelectField
            label="Mức giá"
            options={priceOptions}
            value={priceFilter}
            onChange={(event) => updateFilter("price", event.target.value)}
          />
          <label className="ui-field">
            <span className="ui-field__label">Sắp xếp</span>
            <Tabs value={sortValue} options={sortOptions} onChange={(next) => updateFilter("sort", next)} />
          </label>
          <div className="discover-drawer-actions">
            <Button variant="ghost" onClick={clearFilters} fullWidth>
              Xóa tất cả
            </Button>
            <Button variant="primary" onClick={() => setFilterDrawerOpen(false)} fullWidth>
              Áp dụng
            </Button>
          </div>
        </div>
      </Drawer>
    </div>
  );
}

