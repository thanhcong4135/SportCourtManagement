import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { BottomNavigation } from "../../components/BottomNavigation";
import { useAuth } from "../../context/AuthContext";
import { formatCurrency } from "../../lib/api";
import { toErrorPresentation } from "../../lib/errorPresentation";
import { listBookings, type Booking, type BookingPage } from "../../lib/coreApi";

const statusMap: Record<string, string> = {
  DRAFT: "Chờ đặt cọc",
  CONFIRMED: "Đã xác nhận",
  IN_PROGRESS: "Đang chơi",
  COMPLETED: "Hoàn thành",
  CANCELED: "Đã hủy",
  FAILED_TIMEOUT: "Hết hạn đặt cọc",
};

const paymentMap: Record<string, string> = {
  UNPAID: "Chưa thanh toán",
  DEPOSITED: "Đã đặt cọc",
  PAID: "Đã thanh toán",
  REFUNDED: "Đã hoàn tiền",
  FAILED: "Thanh toán lỗi",
};

const pageSize = 10;

export function AccountPage() {
  const navigate = useNavigate();
  const { token, isAuthenticated, logout } = useAuth();
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [pageIndex, setPageIndex] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [hasPrevious, setHasPrevious] = useState(false);
  const [totalElements, setTotalElements] = useState(0);
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
        const page = await listBookings({
          size: pageSize,
          page: pageIndex,
          status: statusFilter === "ALL" ? undefined : statusFilter,
        });
        const typed = page as BookingPage;
        setBookings(typed.items ?? []);
        setHasNext(Boolean(typed.hasNext));
        setHasPrevious(Boolean(typed.hasPrevious));
        setTotalElements(typed.totalElements ?? 0);
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được danh sách booking");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      } finally {
        setLoading(false);
      }
    }

    void loadBookingRows();
  }, [isAuthenticated, pageIndex, statusFilter]);

  const identityLabel = useMemo(() => {
    if (token?.email) {
      return token.email;
    }
    return "Customer";
  }, [token?.email]);

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
        <button type="button" className="icon-btn" onClick={() => { void logout(); }}>
          Đăng xuất
        </button>
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
            <label>
              Lọc trạng thái
              <select
                value={statusFilter}
                onChange={(event) => {
                  setStatusFilter(event.target.value);
                  setPageIndex(0);
                }}
              >
                <option value="ALL">Tất cả</option>
                <option value="DRAFT">Chờ cọc</option>
                <option value="CONFIRMED">Đã xác nhận</option>
                <option value="IN_PROGRESS">Đang chơi</option>
                <option value="COMPLETED">Hoàn thành</option>
                <option value="CANCELED">Đã hủy</option>
                <option value="FAILED_TIMEOUT">Hết hạn cọc</option>
              </select>
            </label>
          </div>

          {error && <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p>}
          {loading && <p className="inline-muted">Đang tải lịch đặt...</p>}

          <div className="booking-history-list">
            {bookings.map((booking) => (
              <article key={booking.id} className="history-item" onClick={() => navigate(`/account/bookings/${booking.id}`)}>
                <div>
                  <span className="history-tag">Đơn ngày</span>
                  <h3>Sân #{booking.courtId.slice(0, 8)}</h3>
                  <p>Chi tiết: {new Date(booking.startTime).toLocaleString("vi-VN")} - {new Date(booking.endTime).toLocaleString("vi-VN")}</p>
                  <p>Tổng tiền: {formatCurrency(booking.priceTotal)}</p>
                </div>
                <div className="history-status">
                  <strong>{statusMap[booking.status] ?? booking.status}</strong>
                  <p>{paymentMap[booking.paymentStatus] ?? booking.paymentStatus}</p>
                </div>
              </article>
            ))}
            {!loading && bookings.length === 0 && <p className="inline-muted">Không có booking phù hợp.</p>}
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
