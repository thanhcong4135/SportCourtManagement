import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { BottomNavigation } from "../../components/BottomNavigation";
import { StatusBadge, Tabs } from "../../components/ui";
import { useAuth } from "../../context/AuthContext";
import { CustomerNotificationBell } from "../../features/notification/CustomerNotificationBell";
import {
  canDepositBooking,
  getBookingStatusLabel,
  getBookingStatusVariant,
  getDetailActionLabel,
  getPaymentStatusLabel,
  getPaymentStatusVariant,
  isWaitingPaymentBooking,
} from "../../features/booking/bookingPresentation";
import { formatCurrency } from "../../lib/api";
import { toErrorPresentation } from "../../lib/errorPresentation";
import { listBookings, listCourts, listVenues, type Booking } from "../../lib/coreApi";

const pageSize = 10;
const fetchPageSize = 50;
const maxFetchPages = 20;

type BookingTab = "ALL" | "WAITING_PAYMENT" | "UPCOMING" | "COMPLETED" | "CANCELED";

type CourtMeta = {
  courtName: string;
  venueName: string;
};

const tabOptions = [
  { label: "Tất cả", value: "ALL" },
  { label: "Chờ thanh toán", value: "WAITING_PAYMENT" },
  { label: "Sắp tới", value: "UPCOMING" },
  { label: "Hoàn thành", value: "COMPLETED" },
  { label: "Đã hủy", value: "CANCELED" },
];

const upcomingStatuses = new Set(["DRAFT", "CONFIRMED", "IN_PROGRESS"]);
const canceledStatuses = new Set(["CANCELED", "FAILED_TIMEOUT"]);

export function AccountPage() {
  const navigate = useNavigate();
  const { token, isAuthenticated, logout } = useAuth();
  const [allBookings, setAllBookings] = useState<Booking[]>([]);
  const [courtMetaMap, setCourtMetaMap] = useState<Record<string, CourtMeta>>({});
  const [activeTab, setActiveTab] = useState<BookingTab>("ALL");
  const [pageIndex, setPageIndex] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | null>(null);

  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }

    async function loadBookingRows() {
      try {
        setLoading(true);
        setError(null);
        setTraceId(null);

        const rows: Booking[] = [];
        let currentPage = 0;

        while (currentPage < maxFetchPages) {
          const result = await listBookings({
            size: fetchPageSize,
            page: currentPage,
          });

          rows.push(...(result.items ?? []));
          if (!result.hasNext) {
            break;
          }
          currentPage += 1;
        }

        setAllBookings(rows);
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được danh sách booking");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      } finally {
        setLoading(false);
      }
    }

    void loadBookingRows();
  }, [isAuthenticated]);

  useEffect(() => {
    if (!isAuthenticated) {
      return;
    }

    async function loadCourtMeta() {
      try {
        const venues = await listVenues();
        const courtMetaEntries = await Promise.all(
          venues.map(async (venue) => {
            const courts = await listCourts(venue.id);
            return courts.map((court) => [court.id, { courtName: court.name, venueName: venue.name }] as const);
          }),
        );

        setCourtMetaMap(Object.fromEntries(courtMetaEntries.flat()));
      } catch {
        // Court metadata is optional for display; ignore if failed.
      }
    }

    void loadCourtMeta();
  }, [isAuthenticated]);

  useEffect(() => {
    setPageIndex(0);
  }, [activeTab]);

  const identityLabel = useMemo(() => {
    if (token?.email) {
      return token.email;
    }
    return "Customer";
  }, [token?.email]);

  const filteredBookings = useMemo(() => {
    switch (activeTab) {
      case "WAITING_PAYMENT":
        return allBookings.filter((booking) => isWaitingPaymentBooking(booking));
      case "UPCOMING":
        return allBookings.filter((booking) => upcomingStatuses.has(booking.status) && !isWaitingPaymentBooking(booking));
      case "COMPLETED":
        return allBookings.filter((booking) => booking.status === "COMPLETED");
      case "CANCELED":
        return allBookings.filter((booking) => canceledStatuses.has(booking.status));
      default:
        return allBookings;
    }
  }, [activeTab, allBookings]);

  const totalElements = filteredBookings.length;
  const pagedBookings = useMemo(() => {
    const start = pageIndex * pageSize;
    return filteredBookings.slice(start, start + pageSize);
  }, [filteredBookings, pageIndex]);

  const hasPrevious = pageIndex > 0;
  const hasNext = (pageIndex + 1) * pageSize < totalElements;

  function getActionRoute(booking: Booking) {
    if (canDepositBooking(booking)) {
      return `/payment/${booking.id}`;
    }
    return `/account/bookings/${booking.id}`;
  }

  function getCourtDisplay(booking: Booking) {
    const meta = courtMetaMap[booking.courtId];
    if (!meta) {
      return `Sân #${booking.courtId.slice(0, 8)}`;
    }
    return `${meta.courtName} · ${meta.venueName}`;
  }

  if (!isAuthenticated) {
    return (
      <div className="alobo-screen account-screen">
        <header className="simple-topbar">
          <Link to="/discover" className="back-link">←</Link>
          <h1>Tài khoản</h1>
          <div className="topbar-spacer" />
        </header>

        <section className="account-empty">
          <p>Cần đăng nhập để xem danh sách đặt lịch.</p>
          <Link to="/auth/login" className="booking-cta">Đăng nhập</Link>
        </section>

        <BottomNavigation active="account" />
      </div>
    );
  }

  return (
    <div className="alobo-screen account-screen">
      <header className="simple-topbar">
        <Link to="/discover" className="back-link">←</Link>
        <h1>Danh sách đặt lịch</h1>
        <div className="customer-header-actions">
          <CustomerNotificationBell />
          <button type="button" className="icon-btn" onClick={() => { void logout(); }}>
            Đăng xuất
          </button>
        </div>
      </header>

      <section className="account-layout">
        <aside className="account-sidebar">
          <article className="profile-card">
            <div className="avatar">KH</div>
            <div>
              <h3>{identityLabel}</h3>
              <p>{token?.roles?.join(", ") ?? "CUSTOMER"}</p>
            </div>
          </article>

          <article className="sidebar-menu">
            <h4>Hoạt động</h4>
            <button type="button">Nhóm của tôi</button>
            <button type="button">Danh sách lịch học</button>
            <button type="button">Gói hội viên</button>
          </article>

          <article className="sidebar-menu">
            <h4>Hệ thống</h4>
            <button type="button">Cài đặt</button>
            <button type="button">Điều khoản & chính sách</button>
            <button type="button">Thông tin phiên bản</button>
          </article>
        </aside>

        <main className="account-content">
          <div className="account-toolbar">
            <Tabs
              className="account-tabs"
              value={activeTab}
              options={tabOptions}
              onChange={(next) => setActiveTab(next as BookingTab)}
            />
          </div>

          {error && <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p>}
          {loading && <p className="inline-muted">Đang tải lịch đặt...</p>}

          <div className="booking-history-list">
            {pagedBookings.map((booking) => (
              <article key={booking.id} className="history-item" onClick={() => navigate(`/account/bookings/${booking.id}`)}>
                <div>
                  <span className="history-tag">Đơn ngày</span>
                  <h3>{getCourtDisplay(booking)}</h3>
                  <p><strong>Mã booking:</strong> #{booking.id.slice(0, 8)}</p>
                  <p>Chi tiết: {new Date(booking.startTime).toLocaleString("vi-VN")} - {new Date(booking.endTime).toLocaleString("vi-VN")}</p>
                  <p>Tổng tiền: {formatCurrency(booking.priceTotal)}</p>
                </div>
                <div className="history-status">
                  <StatusBadge label={getBookingStatusLabel(booking.status)} variant={getBookingStatusVariant(booking.status)} />
                  <StatusBadge label={getPaymentStatusLabel(booking.paymentStatus)} variant={getPaymentStatusVariant(booking.paymentStatus)} />
                  <button
                    type="button"
                    className="history-action-btn"
                    onClick={(event) => {
                      event.stopPropagation();
                      navigate(getActionRoute(booking));
                    }}
                  >
                    {getDetailActionLabel(booking)}
                  </button>
                </div>
              </article>
            ))}
            {!loading && pagedBookings.length === 0 && <p className="inline-muted">Không có booking phù hợp.</p>}
          </div>

          <div className="pagination-row">
            <button type="button" className="ghost-cta" onClick={() => setPageIndex((prev) => Math.max(0, prev - 1))} disabled={!hasPrevious || loading}>
              Trang trước
            </button>
            <span className="muted">Trang {pageIndex + 1} · Tổng {totalElements}</span>
            <button type="button" className="ghost-cta" onClick={() => setPageIndex((prev) => prev + 1)} disabled={!hasNext || loading}>
              Trang sau
            </button>
          </div>
        </main>
      </section>

      <BottomNavigation active="account" />
    </div>
  );
}
