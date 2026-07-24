import { Link } from "react-router-dom";
import type { NotificationMessage } from "./notificationApi";

type Props = {
  notifications: NotificationMessage[];
  loading: boolean;
  error: string | null;
  markingAll: boolean;
  onNotificationClick: (notification: NotificationMessage) => void;
  onMarkAllRead: () => void;
};

function formatNotificationTime(value: string) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(date);
}

export function CustomerNotificationDropdown({
  notifications,
  loading,
  error,
  markingAll,
  onNotificationClick,
  onMarkAllRead,
}: Props) {
  return (
    <section className="customer-notification-dropdown" aria-label="Thông báo gần đây">
      <header className="customer-notification-dropdown-header">
        <div>
          <strong>Thông báo</strong>
          <span>Cập nhật booking gần đây</span>
        </div>
        <button
          type="button"
          className="customer-notification-text-action"
          disabled={markingAll || notifications.every((item) => !item.unread)}
          onClick={onMarkAllRead}
        >
          {markingAll ? "Đang xử lý..." : "Đánh dấu đã đọc"}
        </button>
      </header>

      <div className="customer-notification-dropdown-body">
        {loading && <p className="customer-notification-state">Đang tải thông báo...</p>}
        {!loading && error && <p className="customer-notification-state is-error">{error}</p>}
        {!loading && !error && notifications.length === 0 && (
          <p className="customer-notification-state">Bạn chưa có thông báo nào.</p>
        )}
        {!loading && !error && notifications.map((notification) => (
          <button
            type="button"
            className={`customer-notification-item${notification.unread ? " is-unread" : ""}`}
            key={notification.id}
            onClick={() => onNotificationClick(notification)}
          >
            <span className="customer-notification-item-copy">
              <strong>{notification.title || "Cập nhật booking"}</strong>
              <span>{notification.message}</span>
              <small>{formatNotificationTime(notification.createdAt)}</small>
            </span>
            {notification.unread && <span className="customer-notification-unread-dot" aria-label="Chưa đọc" />}
          </button>
        ))}
      </div>

      <Link className="customer-notification-view-all" to="/account/notifications">
        Xem tất cả thông báo
      </Link>
    </section>
  );
}
