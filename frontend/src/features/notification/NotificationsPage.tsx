import { useCallback, useEffect, useState } from "react";
import { toErrorPresentation } from "../../lib/errorPresentation";
import { listNotifications, retryNotification, type NotificationMessage, type NotificationStatus } from "./notificationApi";

const statusOptions: Array<NotificationStatus | "ALL"> = ["ALL", "QUEUED", "SENT", "FAILED"];

export function NotificationsPage() {
  const [status, setStatus] = useState<NotificationStatus | "ALL">("ALL");
  const [pageIndex, setPageIndex] = useState(0);
  const [rows, setRows] = useState<NotificationMessage[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [isLast, setIsLast] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const reloadList = useCallback(async () => {
    const page = await listNotifications({
      status: status === "ALL" ? undefined : status,
      page: pageIndex,
      size: 20,
    });
    setRows(page.content ?? []);
    setTotalElements(page.totalElements ?? 0);
    setIsLast(Boolean(page.last));
  }, [pageIndex, status]);

  useEffect(() => {
    async function load() {
      try {
        setLoading(true);
        setError(null);
        setTraceId(null);
        await reloadList();
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được danh sách notification");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      } finally {
        setLoading(false);
      }
    }
    void load();
  }, [reloadList]);

  async function handleRetry(notificationId: string) {
    try {
      setError(null);
      setTraceId(null);
      setNotice(null);
      await retryNotification(notificationId);
      setNotice(`Đã retry notification #${notificationId.slice(0, 8)}.`);
      await reloadList();
    } catch (err) {
      const uiError = toErrorPresentation(err, "Retry notification thất bại");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    }
  }

  return (
    <main className="page">
      <section className="section-header">
        <p className="eyebrow">Notifications</p>
        <h1>Danh sách notification và thao tác retry</h1>
        <p className="muted">Endpoint: /api/notifications (list) · /api/notifications/{`{id}`}/retry</p>
      </section>

      <section className="card">
        <div className="ops-toolbar">
          <label>
            Trạng thái
            <select
              value={status}
              onChange={(event) => {
                setStatus(event.target.value as NotificationStatus | "ALL");
                setPageIndex(0);
              }}
            >
              {statusOptions.map((option) => (
                <option key={option} value={option}>{option}</option>
              ))}
            </select>
          </label>
        </div>

        <div className="ops-table-wrap">
          <table className="ops-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Trạng thái</th>
                <th>Kênh</th>
                <th>Người nhận</th>
                <th>Retry</th>
                <th>TraceId</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <td>#{row.id.slice(0, 8)}</td>
                  <td>{row.status}</td>
                  <td>{row.channel}</td>
                  <td>{row.recipient}</td>
                  <td>{row.retryCount}</td>
                  <td className="muted">{row.traceId ?? "-"}</td>
                  <td>
                    <button
                      className="btn ghost"
                      type="button"
                      onClick={() => { void handleRetry(row.id); }}
                      disabled={row.status === "SENT"}
                    >
                      Retry
                    </button>
                  </td>
                </tr>
              ))}
              {!rows.length && !loading && (
                <tr>
                  <td colSpan={7} className="muted">Không có dữ liệu.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="pagination-row">
          <button className="ghost-cta" type="button" onClick={() => setPageIndex((prev) => Math.max(0, prev - 1))} disabled={pageIndex === 0 || loading}>
            Trang trước
          </button>
          <span className="muted">Trang {pageIndex + 1} · Tổng {totalElements}</span>
          <button className="ghost-cta" type="button" onClick={() => setPageIndex((prev) => prev + 1)} disabled={isLast || loading}>
            Trang sau
          </button>
        </div>
      </section>

      {error && <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p>}
      {notice && <p className="inline-success">{notice}</p>}
    </main>
  );
}

