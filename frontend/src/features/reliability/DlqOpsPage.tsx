import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { toErrorPresentation } from "../../lib/errorPresentation";
import {
  listCoreDlq,
  listPaymentDlq,
  replayCoreDlq,
  replayPaymentDlq,
  type DeadLetterEvent,
  type DeadLetterStatus,
} from "./dlqApi";

type ServiceKey = "core" | "payment";

export function DlqOpsPage() {
  const [service, setService] = useState<ServiceKey>("core");
  const [status, setStatus] = useState<DeadLetterStatus | "ALL">("ALL");
  const [pageIndex, setPageIndex] = useState(0);
  const [rows, setRows] = useState<DeadLetterEvent[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [hasPrevious, setHasPrevious] = useState(false);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    async function load() {
      try {
        setLoading(true);
        setError(null);
        setTraceId(null);
        const effectiveStatus = status === "ALL" ? undefined : status;

        if (service === "core") {
          const page = await listCoreDlq({ status: effectiveStatus, page: pageIndex, size: 20 });
          setRows(page.items ?? []);
          setTotalElements(page.totalElements ?? 0);
          setHasPrevious(Boolean(page.hasPrevious));
          setHasNext(Boolean(page.hasNext));
          return;
        }

        const page = await listPaymentDlq({ status: effectiveStatus, page: pageIndex, size: 20 });
        setRows(page.content ?? []);
        setTotalElements(page.totalElements ?? 0);
        setHasPrevious(!page.first);
        setHasNext(!page.last);
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được DLQ");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      } finally {
        setLoading(false);
      }
    }
    void load();
  }, [pageIndex, service, status]);

  async function handleReplay(eventId: string) {
    try {
      setError(null);
      setTraceId(null);
      setNotice(null);
      if (service === "core") {
        await replayCoreDlq(eventId);
      } else {
        await replayPaymentDlq(eventId);
      }
      setNotice(`Đã replay event #${eventId.slice(0, 8)}.`);
      setRows((prev) => prev.map((row) => (row.id === eventId ? { ...row, status: "REPLAYED", replayCount: row.replayCount + 1 } : row)));
    } catch (err) {
      const uiError = toErrorPresentation(err, "Replay DLQ thất bại");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    }
  }

  return (
    <main className="page">
      <section className="section-header">
        <p className="eyebrow">Reliability Ops</p>
        <h1>DLQ Replay Console</h1>
      </section>

      <section className="card">
        <div className="ops-toolbar">
          <label>
            Service
            <select
              value={service}
              onChange={(event) => {
                setService(event.target.value as ServiceKey);
                setPageIndex(0);
              }}
            >
              <option value="core">core-service</option>
              <option value="payment">payment-service</option>
            </select>
          </label>
          <label>
            Trạng thái
            <select
              value={status}
              onChange={(event) => {
                setStatus(event.target.value as DeadLetterStatus | "ALL");
                setPageIndex(0);
              }}
            >
              <option value="ALL">ALL</option>
              <option value="RECEIVED">RECEIVED</option>
              <option value="REPLAYED">REPLAYED</option>
              <option value="FAILED">FAILED</option>
            </select>
          </label>
          <Link className="btn ghost" to="/ops">Về Ops Portal</Link>
        </div>

        <div className="ops-table-wrap">
          <table className="ops-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Source Topic</th>
                <th>Failure</th>
                <th>Status</th>
                <th>Replay</th>
                <th>Received</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id}>
                  <td>#{row.id.slice(0, 8)}</td>
                  <td>{row.sourceTopic}</td>
                  <td>{row.failureReason ?? "-"}</td>
                  <td>{row.status}</td>
                  <td>{row.replayCount}</td>
                  <td>{new Date(row.receivedAt).toLocaleString("vi-VN")}</td>
                  <td>
                    <button className="btn ghost" type="button" onClick={() => { void handleReplay(row.id); }}>
                      Replay
                    </button>
                  </td>
                </tr>
              ))}
              {!rows.length && !loading && (
                <tr>
                  <td colSpan={7} className="muted">Không có DLQ event.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="pagination-row">
          <button className="ghost-cta" type="button" onClick={() => setPageIndex((prev) => Math.max(0, prev - 1))} disabled={!hasPrevious || loading}>
            Trang trước
          </button>
          <span className="muted">Trang {pageIndex + 1} · Tổng {totalElements}</span>
          <button className="ghost-cta" type="button" onClick={() => setPageIndex((prev) => prev + 1)} disabled={!hasNext || loading}>
            Trang sau
          </button>
        </div>
      </section>

      {error && <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p>}
      {notice && <p className="inline-success">{notice}</p>}
    </main>
  );
}
