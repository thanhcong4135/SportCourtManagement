import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { toErrorPresentation } from "../../lib/errorPresentation";
import { CustomerNotificationDropdown } from "./CustomerNotificationDropdown";
import {
  getMyUnreadNotificationCount,
  listMyNotifications,
  markAllMyNotificationsRead,
  markMyNotificationRead,
  type NotificationMessage,
} from "./notificationApi";

const POLL_INTERVAL_MS = 30_000;

function safeNotificationPath(deepLink?: string) {
  if (!deepLink || !deepLink.startsWith("/") || deepLink.startsWith("//") || deepLink.includes("://")) {
    return "/account/notifications";
  }
  return deepLink;
}

export function CustomerNotificationBell() {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const containerRef = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [notifications, setNotifications] = useState<NotificationMessage[]>([]);
  const [loading, setLoading] = useState(false);
  const [markingAll, setMarkingAll] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refreshUnreadCount = useCallback(async () => {
    if (!isAuthenticated || document.visibilityState !== "visible") {
      return;
    }
    try {
      const response = await getMyUnreadNotificationCount();
      setUnreadCount(response.count);
    } catch {
      // Polling must not interrupt the current customer flow.
    }
  }, [isAuthenticated]);

  const loadLatest = useCallback(async () => {
    if (!isAuthenticated) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const response = await listMyNotifications({ page: 0, size: 5 });
      setNotifications(response.content);
    } catch (requestError) {
      setError(toErrorPresentation(requestError, "Không tải được thông báo").message);
    } finally {
      setLoading(false);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    if (!isAuthenticated) {
      setOpen(false);
      setUnreadCount(0);
      setNotifications([]);
      return undefined;
    }

    void refreshUnreadCount();
    const intervalId = window.setInterval(() => {
      void refreshUnreadCount();
    }, POLL_INTERVAL_MS);
    const refreshWhenActive = () => {
      if (document.visibilityState === "visible") {
        void refreshUnreadCount();
      }
    };
    window.addEventListener("focus", refreshWhenActive);
    document.addEventListener("visibilitychange", refreshWhenActive);

    return () => {
      window.clearInterval(intervalId);
      window.removeEventListener("focus", refreshWhenActive);
      document.removeEventListener("visibilitychange", refreshWhenActive);
    };
  }, [isAuthenticated, refreshUnreadCount]);

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    void loadLatest();
    const closeOnOutsideClick = (event: MouseEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", closeOnOutsideClick);
    return () => document.removeEventListener("mousedown", closeOnOutsideClick);
  }, [loadLatest, open]);

  if (!isAuthenticated) {
    return null;
  }

  async function handleNotificationClick(notification: NotificationMessage) {
    if (notification.unread) {
      try {
        await markMyNotificationRead(notification.id);
        setUnreadCount((current) => Math.max(0, current - 1));
      } catch {
        // Navigation is still useful if the read-state update temporarily fails.
      }
    }
    setOpen(false);
    navigate(safeNotificationPath(notification.deepLink));
  }

  async function handleMarkAllRead() {
    setMarkingAll(true);
    setError(null);
    try {
      await markAllMyNotificationsRead();
      setUnreadCount(0);
      setNotifications((current) => current.map((item) => ({ ...item, unread: false })));
    } catch (requestError) {
      setError(toErrorPresentation(requestError, "Không thể đánh dấu thông báo").message);
    } finally {
      setMarkingAll(false);
    }
  }

  return (
    <div className="customer-notification-bell" ref={containerRef}>
      <button
        type="button"
        className={`customer-notification-trigger${open ? " is-open" : ""}`}
        aria-expanded={open}
        aria-haspopup="dialog"
        onClick={() => setOpen((current) => !current)}
      >
        Thông báo
        {unreadCount > 0 && (
          <span className="customer-notification-count">
            {unreadCount > 99 ? "99+" : unreadCount}
          </span>
        )}
      </button>
      {open && (
        <CustomerNotificationDropdown
          notifications={notifications}
          loading={loading}
          error={error}
          markingAll={markingAll}
          onNotificationClick={(notification) => { void handleNotificationClick(notification); }}
          onMarkAllRead={() => { void handleMarkAllRead(); }}
        />
      )}
    </div>
  );
}
