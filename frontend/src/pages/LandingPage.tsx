import { useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { HeroSearchBox } from "../components/booking/HeroSearchBox";
import { SportCategoryCard } from "../components/booking/SportCategoryCard";
import { VenueCard } from "../components/booking/VenueCard";
import { Button, SkeletonCard } from "../components/ui";
import { useAuth } from "../context/AuthContext";
import { sportCategories, venueGalleryPlaceholders } from "../data/mockMedia";
import { useDiscoverData } from "../features/venues/useDiscoverData";
import { trackEvent } from "../lib/analytics";

function getTodayIsoDate() {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, "0");
  const d = String(now.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

const stepItems = [
  { title: "Tìm sân phù hợp", desc: "Chọn môn thể thao, khu vực và ngày chơi." },
  { title: "Chọn khung giờ trống", desc: "Xem trạng thái slot theo từng sân con." },
  { title: "Xác nhận & thanh toán", desc: "Đặt cọc hoặc thanh toán theo hình thức phù hợp." },
];

export function LandingPage() {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const discoverQuery = useDiscoverData();

  const [keyword, setKeyword] = useState("");
  const [sport, setSport] = useState("PICKLEBALL");
  const [area, setArea] = useState("ALL");
  const [date, setDate] = useState(getTodayIsoDate());
  const [isSearchFormVisible, setIsSearchFormVisible] = useState(false);

  const areaOptions = useMemo(() => {
    const options = (discoverQuery.data?.venues ?? [])
      .map((venue) => ({ label: venue.address, value: venue.id }));
    return [{ label: "Tất cả khu vực", value: "ALL" }, ...options];
  }, [discoverQuery.data?.venues]);

  const sportOptions = [
    { label: "Pickleball", value: "PICKLEBALL" },
    { label: "Cầu lông", value: "BADMINTON" },
    { label: "Tennis", value: "TENNIS" },
    { label: "Bóng đá", value: "FOOTBALL" },
  ];

  const featuredCards = useMemo(() => {
    const venues = discoverQuery.data?.venues ?? [];
    const courtsByVenue = discoverQuery.data?.courtsByVenue ?? {};
    return venues
      .flatMap((venue) => (courtsByVenue[venue.id] ?? []).slice(0, 1).map((court) => ({ venue, court })))
      .slice(0, 6);
  }, [discoverQuery.data?.courtsByVenue, discoverQuery.data?.venues]);

  function goSearch() {
    trackEvent("landing_search_submit", { sport, area, date, hasKeyword: Boolean(keyword.trim()) });
    const params = new URLSearchParams();
    if (keyword.trim()) {
      params.set("q", keyword.trim());
    }
    if (sport !== "ALL") {
      params.set("sport", sport);
    }
    if (date) {
      params.set("date", date);
    }
    navigate(`/discover?${params.toString()}`);
  }

  return (
    <main className="sportcourt-home">
      <header className="home-topnav">
        <Link to="/" className="brand">
          <span className="brand-mark">SC</span>
          <div>
            <strong>SportCourtManagement</strong>
            <small>Nền tảng đặt sân thể thao</small>
          </div>
        </Link>
        <nav>
          <Link to="/discover">Khám phá sân</Link>
          <Link to="/account">Lịch đã đặt</Link>
          <Link to="/ops">Vận hành sân</Link>
        </nav>
        <Link to={isAuthenticated ? "/account" : "/auth/login"} className="pill-link">
          {isAuthenticated ? "Tài khoản" : "Đăng nhập"}
        </Link>
      </header>

      <section className="home-hero">
        <div className="home-hero-copy">
          <h1>Tìm sân, chọn giờ trống và hoàn tất đặt lịch trong vài phút</h1>
          <p>
            Nền tảng đặt sân thể thao giúp người chơi tìm sân nhanh chóng, chọn lịch trống chính xác và hỗ trợ chủ sân
            quản lý vận hành hiệu quả
          </p>
        </div>
        <div className="hero-search-area">
          <div className="hero-top-actions">
            <Link to="/discover" className="ui-button ui-button--secondary ui-button--lg">Xem toàn bộ sân</Link>
            <Button
              variant="primary"
              size="lg"
              aria-expanded={isSearchFormVisible}
              aria-controls="hero-search-form"
              onClick={() => setIsSearchFormVisible((visible) => !visible)}
            >
              {isSearchFormVisible ? "Đóng tìm kiếm" : "Tìm sân gần bạn"}
            </Button>
          </div>
          {isSearchFormVisible ? (
            <HeroSearchBox
              keyword={keyword}
              sport={sport}
              area={area}
              date={date}
              onKeywordChange={setKeyword}
              onSportChange={setSport}
              onAreaChange={setArea}
              onDateChange={setDate}
              onSubmit={goSearch}
              autoFocusKeyword
              sportOptions={sportOptions}
              areaOptions={areaOptions}
              submitLabel="Tìm sân trống"
            />
          ) : null}
        </div>
      </section>

      <section className="home-section">
        <div className="sport-category-grid">
          {sportCategories.map((item) => (
            <SportCategoryCard
              key={item.key}
              title={item.label}
              description={item.desc}
              backgroundImage={item.backgroundImage}
              to={`/discover?sport=${item.key}`}
            />
          ))}
        </div>
      </section>

      <section className="home-section">
        <div className="section-title">
          <h2>Sân/chi nhánh nổi bật</h2>
          <p>Những sân được tìm kiếm nhiều trong tuần này.</p>
        </div>
        <div className="featured-venues-grid">
          {discoverQuery.isLoading ? <SkeletonCard count={3} /> : null}
          {!discoverQuery.isLoading && featuredCards.map((row, index) => (
            <VenueCard
              key={`${row.venue.id}-${row.court.id}`}
              venueName={row.venue.name}
              courtName={row.court.name}
              sportType={row.court.sportType}
              address={row.venue.address}
              distanceKm={Number((index + 2.2).toFixed(1))}
              rating={Number((4.4 + ((index % 4) * 0.1)).toFixed(1))}
              openingHours="05:00 - 24:00"
              priceLabel="Từ 120.000đ"
              availabilityLabel="Còn slot"
              availabilityVariant="success"
              amenities={["Bãi xe", "Nhà tắm", "Nước uống"]}
              bannerStyle={venueGalleryPlaceholders[index % venueGalleryPlaceholders.length]}
              bannerImageUrl={row.venue.coverImageUrl ?? row.venue.imageUrl ?? null}
              onBook={() => navigate(`/venues/${row.venue.id}?courtId=${row.court.id}`)}
              actionLabel="Đặt sân"
            />
          ))}
        </div>
      </section>

      <section className="home-section home-steps">
        <div className="section-title">
          <h2>Đặt sân trong 3 bước</h2>
          <p>Luồng đặt sân tối ưu cho mobile và desktop.</p>
        </div>
        <div className="steps-grid">
          {stepItems.map((item, index) => (
            <article key={item.title} className="step-card">
              <span>{index + 1}</span>
              <strong>{item.title}</strong>
              <p>{item.desc}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="home-cta">
        <h3>Sẵn sàng đặt lịch cho buổi chơi tiếp theo?</h3>
        <p>Vào ngay màn khám phá để xem lịch trống theo thời gian thực.</p>
        <Button variant="primary" size="lg" onClick={() => navigate("/discover")}>Bắt đầu đặt sân</Button>
      </section>
    </main>
  );
}

export default LandingPage;
