import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import { z } from "zod";
import { Button, InputField, SelectField } from "../components/ui";
import { useAuth } from "../context/AuthContext";
import { trackEvent } from "../lib/analytics";
import { getApiBaseUrl } from "../lib/api";

const quickSearchSchema = z.object({
  q: z.string().trim().min(1, "Vui lòng nhập địa điểm hoặc tên sân"),
  sport: z.string().trim().min(1),
  date: z.string().trim().min(1),
  time: z.string().trim().min(1),
});

type QuickSearchValues = z.infer<typeof quickSearchSchema>;

function getTodayIsoDate() {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, "0");
  const d = String(now.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

const sportOptions = [
  { label: "PICKLEBALL", value: "PICKLEBALL" },
  { label: "BADMINTON", value: "BADMINTON" },
  { label: "TENNIS", value: "TENNIS" },
];

const featuredQuickLinks = [
  { title: "Sân gần bạn", subtitle: "Ưu tiên khoảng cách gần", to: "/discover?sort=NEAREST" },
  { title: "Giá dễ đặt", subtitle: "Ưu tiên dưới 100.000đ", to: "/discover?price=UNDER_100K&sort=PRICE_LOW" },
  { title: "Giờ cao điểm", subtitle: "Xem nhanh slot tối nay", to: "/discover?sport=PICKLEBALL" },
];

export function LandingPage() {
  const navigate = useNavigate();
  const { isAuthenticated, token } = useAuth();
  const form = useForm<QuickSearchValues>({
    resolver: zodResolver(quickSearchSchema),
    defaultValues: {
      q: "",
      sport: "PICKLEBALL",
      date: getTodayIsoDate(),
      time: "18:00",
    },
  });

  function onSubmit(values: QuickSearchValues) {
    trackEvent("funnel_landing_search_submit", {
      sport: values.sport,
      date: values.date,
      time: values.time,
      keywordLength: values.q.trim().length,
    });
    const params = new URLSearchParams({
      q: values.q,
      sport: values.sport,
      date: values.date,
      time: values.time,
    });
    navigate(`/discover?${params.toString()}`);
  }

  return (
    <main className="landing-page">
      <section className="landing-hero">
        <div className="landing-hero-content">
          <p className="landing-kicker">Booking-first experience</p>
          <h1>Tìm sân nhanh, xem giờ trống, đặt lịch online trong vài bước</h1>
          <p>
            Trang chủ tập trung vào hành trình đặt sân: tìm sân phù hợp, lọc theo môn và thời gian,
            sau đó đi thẳng vào màn chọn slot trực quan.
          </p>
          <div className="landing-actions">
            <Link
              to="/discover"
              className="ui-button ui-button--secondary ui-button--md"
              onClick={() => trackEvent("funnel_landing_explore_click")}
            >
              Khám phá tất cả sân
            </Link>
            <Link
              to={isAuthenticated ? "/account" : "/auth/login"}
              className="ui-button ui-button--secondary ui-button--md"
              onClick={() => trackEvent("funnel_landing_auth_cta_click", { authenticated: isAuthenticated })}
            >
              {isAuthenticated ? "Tài khoản" : "Đăng nhập"}
            </Link>
          </div>
          {isAuthenticated && token?.email ? (
            <p className="landing-api">Đang đăng nhập: <code>{token.email}</code></p>
          ) : null}
          <p className="landing-api">API base: <code>{getApiBaseUrl()}</code></p>
        </div>

        <form className="landing-search-card" onSubmit={form.handleSubmit(onSubmit)}>
          <h3>Tìm nhanh khung giờ phù hợp</h3>
          <InputField
            label="Khu vực / tên sân"
            placeholder="Ví dụ: Gò Vấp, Thủ Đức, Pickleball"
            {...form.register("q")}
            error={form.formState.errors.q?.message}
          />
          <div className="landing-search-grid">
            <SelectField
              label="Môn thể thao"
              options={sportOptions}
              {...form.register("sport")}
              error={form.formState.errors.sport?.message}
            />
            <InputField
              label="Ngày chơi"
              type="date"
              {...form.register("date")}
              error={form.formState.errors.date?.message}
            />
            <InputField
              label="Giờ mong muốn"
              type="time"
              {...form.register("time")}
              error={form.formState.errors.time?.message}
            />
          </div>
          <Button type="submit" variant="primary" size="lg" fullWidth>
            Tìm sân ngay
          </Button>
        </form>
      </section>

      <section className="landing-featured">
        <header>
          <h2>Khám phá nhanh theo nhu cầu phổ biến</h2>
          <p>Chọn nhanh một kịch bản để đi thẳng vào danh sách sân đã lọc.</p>
        </header>
        <div className="landing-featured-grid">
          {featuredQuickLinks.map((item) => (
            <Link
              key={item.title}
              to={item.to}
              className="landing-featured-card"
              onClick={() => trackEvent("funnel_landing_quick_link_click", { title: item.title, to: item.to })}
            >
              <strong>{item.title}</strong>
              <span>{item.subtitle}</span>
            </Link>
          ))}
        </div>
      </section>
    </main>
  );
}
