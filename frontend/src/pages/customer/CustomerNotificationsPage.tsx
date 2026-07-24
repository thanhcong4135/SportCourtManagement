import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { BottomNavigation } from "../../components/BottomNavigation";
import {
  listMyNotifications,
  markAllMyNotificationsRead,
  markMyNotificationRead,
  type NotificationMessage,
} from "../../features/notification/notificationApi";
import { toErrorPresentation } from "../../lib/errorPresentation";

const PAGE_SIZE = 12;

function formatNotificationTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

function safeNotificationPath(deepLink?: string) {
  if (!deepLink || !deepLink.startsWith("/") || deepLink.startsWith("//") || deepLink.includes("://")) {
    return "/account";
  }
  return deepLink;
}

export function CustomerNotificationsPage() {
  const navigate = useNavigate();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [notifications, setNotifications] = useState<NotificationMessage[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [markingAll, setMarkingAll] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadNotifications = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await listMyNotifications({ unreadOnly, page, size: PAGE_SIZE });
      setNotifications(response.content);
      setTotalPages(response.totalPages);
    } catch (requestError) {
      setError(toErrorPresentation(requestError, "Không tải được thông báo").message);
    } finally {
      setLoading(false);
    }
  }, [page, unreadOnly]);

  useEffect(() => {
    void loadNotifications();
  }, [loadNotifications]);

  async function openNotification(notification: NotificationMessage) {
    if (notification.unread) {
      try {
        await markMyNotificationRead(notification.id);
      } catch {
        // Keep the destination available when the read update temporarily fails.
      }
    }
    navigate(safeNotificationPath(notification.deepLink));
  }

  async function markAllRead() {
    setMarkingAll(true);
    setError(null);
    try {
      await markAllMyNotificationsRead();
      if (unreadOnly) {
        setPage(0);
        await loadNotifications();
      } else {
        setNotifications((current) => current.map((notification) => ({ ...notification, unread: false })));
      }
    } catch (requestError) {
      setError(toErrorPresentation(requestError, "Không thể đánh dấu thông báo").message);
    } finally {
      setMarkingAll(false);
    }
  }

  function changeFilter(nextUnreadOnly: boolean) {
    setUnreadOnly(nextUnreadOnly);
    setPage(0);
  }

  return (
    <div className="alobo-screen customer-notifications-screen">
      <header className="simple-topbar">
        <Link to="/account" className="back-link" aria-label="Quay lại tài khoản">←</Link>
        <h1>Thông báo</h1>
        <button
          type="button"
          className="customer-notification-text-action"
          disabled={markingAll || notifications.every((item) => !item.unread)}
          onClick={() => { void markAllRead(); }}
        >
          {markingAll ? "Đang xử lý..." : "Đánh dấu tất cả đã đọc"}
        </button>
      </header>

      <main className="customer-notifications-content">
        <div className="customer-notification-filters" role="tablist" aria-label="Lọc thông báo">
          <button
            type="button"
            role="tab"
            aria-selected={!unreadOnly}
            className={!unreadOnly ? "is-active" : ""}
            onClick={() => changeFilter(false)}
          >
            Tất cả
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={unreadOnly}
            className={unreadOnly ? "is-active" : ""}
            onClick={() => changeFilter(true)}
          >
            Chưa đọc
          </button>
        </div>

        {loading && <p className="customer-notification-page-state">Đang tải thông báo...</p>}
        {!loading && error && (
          <div className="customer-notification-page-state is-error">
            <p>{error}</p>
            <button type="button" onClick={() => { void loadNotifications(); }}>Thử lại</button>
          </div>
        )}
        {!loading && !error && notifications.length === 0 && (
          <p className="customer-notification-page-state">
            {unreadOnly ? "Bạn không có thông báo chưa đọc." : "Bạn chưa có thông báo nào."}
          </p>
        )}
        {!loading && !error && notifications.length > 0 && (
          <section className="customer-notification-page-list" aria-label="Danh sách thông báo">
            {notifications.map((notification) => (
              <button
                type="button"
                key={notification.id}
                className={`customer-notification-page-item${notification.unread ? " is-unread" : ""}`}
                onClick={() => { void openNotification(notification); }}
              >
                <span className="customer-notification-item-copy">
                  <strong>{notification.title || "Cập nhật booking"}</strong>
                  <span>{notification.message}</span>
                  <small>{formatNotificationTime(notification.createdAt)}</small>
                </span>
                {notification.unread && <span className="customer-notification-unread-dot" aria-label="Chưa đọc" />}
              </button>
            ))}
          </section>
        )}

        {totalPages > 1 && (
          <nav className="customer-notification-pagination" aria-label="Phân trang thông báo">
            <button type="button" disabled={page === 0 || loading} onClick={() => setPage((current) => current - 1)}>
              Trang trước
            </button>
            <span>{page + 1} / {totalPages}</span>
            <button
              type="button"
              disabled={page >= totalPages - 1 || loading}
              onClick={() => setPage((current) => current + 1)}
            >
              Trang sau
            </button>
          </nav>
        )}
      </main>

      <BottomNavigation active="account" />
    </div>
  );
}
