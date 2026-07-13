import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  OPS_DLQ_ROLES,
  OPS_NOTIFICATIONS_ROLES,
  OPS_PRICING_ROLES,
  OPS_USER_MANAGEMENT_ROLES,
} from "../../app/routeRolePolicy";
import { apiFetch, createIdempotencyKey, formatCurrency } from "../../lib/api";
import { useAuth } from "../../context/AuthContext";
import { toErrorPresentation } from "../../lib/errorPresentation";
import { listPricingRules } from "../../lib/coreApi";

type Venue = { id: string; name: string; address: string };
type Court = { id: string; venueId: string; name: string; sportType: string };
type Product = { id: string; venueId: string; name: string; unitPrice: number; active: boolean };
type ReportPage<T> = { items: T[]; totalElements: number; totalPages: number };
type OccupancyRow = { reportDate: string; venueId: string; totalBookings: number; bookedHours: number };
type RevenueRow = { reportDate: string; venueId: string; bookingRevenue: number; depositRevenue: number; addOnRevenue: number; totalRevenue: number };
type BestHourRow = { hourOfDay: number; bookingCount: number; bookedHours: number };

const sports = ["BADMINTON", "PICKLEBALL", "FOOTBALL"];

export function OpsPortalPage() {
  const { token, hasAnyRole, isAuthenticated } = useAuth();
  const [venues, setVenues] = useState<Venue[]>([]);
  const [courts, setCourts] = useState<Court[]>([]);
  const [products, setProducts] = useState<Product[]>([]);

  const [venueName, setVenueName] = useState("");
  const [venueAddress, setVenueAddress] = useState("");
  const [editVenueName, setEditVenueName] = useState("");
  const [editVenueAddress, setEditVenueAddress] = useState("");
  const [selectedVenueId, setSelectedVenueId] = useState("");
  const [courtName, setCourtName] = useState("");
  const [sportType, setSportType] = useState(sports[0]);
  const [productName, setProductName] = useState("");
  const [unitPrice, setUnitPrice] = useState<number>(25000);

  const [fromDate, setFromDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [toDate, setToDate] = useState(() => new Date(Date.now() + 86400000).toISOString().slice(0, 10));
  const [occupancy, setOccupancy] = useState<OccupancyRow[]>([]);
  const [revenue, setRevenue] = useState<RevenueRow[]>([]);
  const [bestHours, setBestHours] = useState<BestHourRow[]>([]);
  const [pricingRuleCountByCourt, setPricingRuleCountByCourt] = useState<Record<string, number>>({});

  const [busy, setBusy] = useState(false);
  const [pricingHealthLoading, setPricingHealthLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const canWrite = isAuthenticated;
  const selectedVenue = useMemo(() => venues.find((item) => item.id === selectedVenueId) ?? null, [venues, selectedVenueId]);

  const loadVenues = useCallback(async () => {
    try {
      setError(null);
      setTraceId(null);
      const data = await apiFetch<Venue[]>("/api/core/venues");
      setVenues(data);
      if (!selectedVenueId && data[0]) {
        setSelectedVenueId(data[0].id);
      }
    } catch (err) {
      const uiError = toErrorPresentation(err, "Không tải được venue");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    }
  }, [selectedVenueId]);

  const loadVenueData = useCallback(async (venueId: string) => {
    try {
      setError(null);
      setTraceId(null);
      const [courtRows, productRows] = await Promise.all([
        apiFetch<Court[]>(`/api/core/courts?venueId=${venueId}`),
        apiFetch<Product[]>(`/api/core/products?venueId=${venueId}`),
      ]);
      setCourts(courtRows);
      setProducts(productRows);
      setPricingHealthLoading(true);
      const pricingCounts = await Promise.all(
        courtRows.map(async (court) => {
          const rows = await listPricingRules(court.id);
          return [court.id, rows.length] as const;
        }),
      );
      setPricingRuleCountByCourt(Object.fromEntries(pricingCounts));
    } catch (err) {
      const uiError = toErrorPresentation(err, "Không tải được dữ liệu venue");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setPricingHealthLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadVenues();
  }, [loadVenues]);

  useEffect(() => {
    if (!selectedVenueId) {
      setCourts([]);
      setProducts([]);
      setPricingRuleCountByCourt({});
      return;
    }
    void loadVenueData(selectedVenueId);
  }, [loadVenueData, selectedVenueId]);

  useEffect(() => {
    setEditVenueName(selectedVenue?.name ?? "");
    setEditVenueAddress(selectedVenue?.address ?? "");
  }, [selectedVenue?.address, selectedVenue?.id, selectedVenue?.name]);

  async function createVenue() {
    if (!token?.accessToken) {
      setError("Cần đăng nhập OWNER/ADMIN");
      setTraceId(null);
      return;
    }
    try {
      setBusy(true);
      setError(null);
      setTraceId(null);
      setNotice(null);
      await apiFetch("/api/core/venues", {
        method: "POST",
        headers: {
          "Idempotency-Key": createIdempotencyKey("ops-venue-create"),
        },
        body: JSON.stringify({ name: venueName, address: venueAddress }),
      }, token.accessToken);
      setVenueName("");
      setVenueAddress("");
      await loadVenues();
      setNotice("Đã tạo venue");
    } catch (err) {
      const uiError = toErrorPresentation(err, "Tạo venue thất bại");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  async function updateVenue() {
    if (!token?.accessToken || !selectedVenueId) {
      setError("Cần đăng nhập và chọn venue");
      setTraceId(null);
      return;
    }

    const nextName = editVenueName.trim();
    const nextAddress = editVenueAddress.trim();
    if (!nextName || !nextAddress) {
      setError("Tên venue và địa chỉ không được bỏ trống");
      setTraceId(null);
      return;
    }

    try {
      setBusy(true);
      setError(null);
      setTraceId(null);
      setNotice(null);
      const updated = await apiFetch<Venue>(`/api/core/venues/${selectedVenueId}`, {
        method: "PUT",
        headers: {
          "Idempotency-Key": createIdempotencyKey("ops-venue-update"),
        },
        body: JSON.stringify({ name: nextName, address: nextAddress }),
      }, token.accessToken);
      setVenues((current) => current.map((venue) => (venue.id === updated.id ? updated : venue)));
      setNotice("Đã cập nhật thông tin venue");
    } catch (err) {
      const uiError = toErrorPresentation(err, "Cập nhật venue thất bại");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  async function createCourt() {
    if (!token?.accessToken || !selectedVenueId) {
      setError("Cần đăng nhập và chọn venue");
      setTraceId(null);
      return;
    }
    try {
      setBusy(true);
      setError(null);
      setTraceId(null);
      setNotice(null);
      await apiFetch("/api/core/courts", {
        method: "POST",
        headers: {
          "Idempotency-Key": createIdempotencyKey("ops-court-create"),
        },
        body: JSON.stringify({ venueId: selectedVenueId, name: courtName, sportType }),
      }, token.accessToken);
      setCourtName("");
      await loadVenueData(selectedVenueId);
      setNotice("Đã tạo sân");
    } catch (err) {
      const uiError = toErrorPresentation(err, "Tạo sân thất bại");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  async function createProduct() {
    if (!token?.accessToken || !selectedVenueId) {
      setError("Cần đăng nhập và chọn venue");
      setTraceId(null);
      return;
    }
    try {
      setBusy(true);
      setError(null);
      setTraceId(null);
      setNotice(null);
      await apiFetch("/api/core/products", {
        method: "POST",
        headers: {
          "Idempotency-Key": createIdempotencyKey("ops-product-create"),
        },
        body: JSON.stringify({ venueId: selectedVenueId, name: productName, unitPrice, active: true }),
      }, token.accessToken);
      setProductName("");
      await loadVenueData(selectedVenueId);
      setNotice("Đã tạo sản phẩm");
    } catch (err) {
      const uiError = toErrorPresentation(err, "Tạo sản phẩm thất bại");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  async function loadReports() {
    if (!selectedVenueId) {
      setError("Cần chọn venue");
      setTraceId(null);
      return;
    }

    try {
      setBusy(true);
      setError(null);
      setTraceId(null);
      setNotice(null);
      const [occ, rev, best] = await Promise.all([
        apiFetch<ReportPage<OccupancyRow>>(`/api/reports/occupancy?from=${fromDate}&to=${toDate}&venueId=${selectedVenueId}&size=30&page=0`),
        apiFetch<ReportPage<RevenueRow>>(`/api/reports/revenue?from=${fromDate}&to=${toDate}&venueId=${selectedVenueId}&size=30&page=0`),
        apiFetch<BestHourRow[]>(`/api/reports/best-hours?from=${fromDate}&to=${toDate}&venueId=${selectedVenueId}&top=5`),
      ]);
      setOccupancy(occ.items ?? []);
      setRevenue(rev.items ?? []);
      setBestHours(best ?? []);
      setNotice("Đã tải báo cáo.");
    } catch (err) {
      const uiError = toErrorPresentation(err, "Không tải được báo cáo");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  const totalBookings = occupancy.reduce((sum, row) => sum + row.totalBookings, 0);
  const totalHours = occupancy.reduce((sum, row) => sum + Number(row.bookedHours), 0);
  const totalRevenue = revenue.reduce((sum, row) => sum + Number(row.totalRevenue), 0);
  const totalAddOn = revenue.reduce((sum, row) => sum + Number(row.addOnRevenue), 0);
  const pricingCoveredCourtCount = courts.filter((court) => (pricingRuleCountByCourt[court.id] ?? 0) > 0).length;
  const missingPricingCourts = courts.filter((court) => (pricingRuleCountByCourt[court.id] ?? 0) === 0);
  const pricingCoveragePercent = courts.length ? Math.round((pricingCoveredCourtCount / courts.length) * 100) : 0;
  const topBestHour = bestHours[0];
  const topBestHourLabel = topBestHour
    ? `${String(topBestHour.hourOfDay).padStart(2, "0")}:00 · ${topBestHour.bookingCount} lượt`
    : "Chưa có dữ liệu";

  return (
    <main className="page ops-layout-page">
      <section className="section-header ops-header-card">
        <div className="ops-header-main">
          <p className="eyebrow">SportCourt Operations</p>
          <h1>Dashboard vận hành sân</h1>
          <p className="muted">Materio-style: KPI nổi bật, insight nhanh, thao tác vận hành tập trung.</p>
          {!canWrite && <p className="muted">Bạn đang ở chế độ chỉ xem. Cần OWNER/ADMIN để tạo dữ liệu.</p>}
        </div>
        <div className="ops-header-side">
          <p><span>Venue hiện tại:</span> <strong>{selectedVenue?.name ?? "Chưa chọn"}</strong></p>
          <p><span>Khoảng ngày:</span> <strong>{fromDate} → {toDate}</strong></p>
          <p><span>Giờ cao điểm:</span> <strong>{topBestHourLabel}</strong></p>
        </div>
      </section>

      <section className="ops-link-grid">
        <div className="ops-toolbar">
          {hasAnyRole(OPS_NOTIFICATIONS_ROLES) ? <Link className="btn ghost" to="/ops/notifications">Thông báo</Link> : null}
          {hasAnyRole(OPS_PRICING_ROLES) ? <Link className="btn ghost" to="/ops/pricing-rules">Pricing Rules</Link> : null}
          {hasAnyRole(OPS_USER_MANAGEMENT_ROLES) ? <Link className="btn ghost" to="/ops/admin/users">Quản lý người dùng</Link> : null}
          {hasAnyRole(OPS_DLQ_ROLES) ? <Link className="btn ghost" to="/ops/dlq">DLQ Replay</Link> : null}
          <Link className="btn ghost" to="/booking/batch">Batch Booking</Link>
        </div>
      </section>

      <section className="grid four kpi-grid">
        <article className="card kpi-card">
          <span className="kpi-chip kpi-chip-info">Bookings</span>
          <strong>{totalBookings}</strong>
          <small>Tổng lượt đặt theo khoảng ngày đã chọn</small>
        </article>
        <article className="card kpi-card">
          <span className="kpi-chip kpi-chip-info">Booked Hours</span>
          <strong>{totalHours.toFixed(1)}h</strong>
          <small>Tổng số giờ khai thác thực tế</small>
        </article>
        <article className="card kpi-card">
          <span className="kpi-chip kpi-chip-success">Total Revenue</span>
          <strong>{formatCurrency(totalRevenue)}</strong>
          <small>Doanh thu booking + đặt cọc + add-on</small>
        </article>
        <article className="card kpi-card">
          <span className="kpi-chip kpi-chip-accent">Add-on Revenue</span>
          <strong>{formatCurrency(totalAddOn)}</strong>
          <small>Doanh thu dịch vụ đi kèm</small>
        </article>
        <article className="card kpi-card">
          <span className="kpi-chip kpi-chip-warning">Pricing Coverage</span>
          <strong>{pricingCoveragePercent}%</strong>
          <small>{pricingCoveredCourtCount}/{courts.length || 0} sân đã có rule</small>
        </article>
        <article className="card kpi-card">
          <span className="kpi-chip kpi-chip-danger">Sân thiếu pricing</span>
          <strong>{missingPricingCourts.length}</strong>
          <small>{pricingHealthLoading ? "Đang kiểm tra..." : "Cần tạo baseline/rule chi tiết"}</small>
        </article>
      </section>

      <section className="grid two ops-insight-grid">
        <article className="card">
          <h3>Insight vận hành nhanh</h3>
          <ul className="list-clean">
            <li>Giờ cao điểm hiện tại: <strong>{topBestHourLabel}</strong></li>
            <li>Tỉ lệ phủ pricing: <strong>{pricingCoveragePercent}%</strong></li>
            <li>Tổng số sản phẩm đang bán: <strong>{products.length}</strong></li>
          </ul>
        </article>
        <article className="card">
          <h3>Cảnh báo cần xử lý</h3>
          {missingPricingCourts.length > 0 ? (
            <ul className="list-clean">
              {missingPricingCourts.slice(0, 3).map((court) => (
                <li key={court.id}>{court.name} chưa có pricing rule</li>
              ))}
              {missingPricingCourts.length > 3 && <li>+{missingPricingCourts.length - 3} sân khác</li>}
            </ul>
          ) : (
            <p className="inline-success">Không có cảnh báo pricing trong venue đã chọn.</p>
          )}
        </article>
      </section>

      <section className="ops-shell">
        <article className="card ops-main">
          <div className="ops-toolbar">
            <label>Cụm sân
              <select value={selectedVenueId} onChange={(e) => setSelectedVenueId(e.target.value)}>
                <option value="">-- chọn venue --</option>
                {venues.map((venue) => <option key={venue.id} value={venue.id}>{venue.name}</option>)}
              </select>
            </label>
            <label>Từ ngày <input type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)} /></label>
            <label>Đến ngày <input type="date" value={toDate} onChange={(e) => setToDate(e.target.value)} /></label>
            <button className="btn" onClick={() => { void loadReports(); }} disabled={busy}>Tải báo cáo</button>
          </div>

          <h3>Lịch sử sử dụng theo ngày</h3>
          <div className="ops-table-wrap">
            <table className="ops-table">
              <thead>
                <tr>
                  <th>Ngày</th>
                  <th>Số booking</th>
                  <th>Số giờ</th>
                  <th>Doanh thu</th>
                  <th>Đặt cọc</th>
                  <th>Add-on</th>
                </tr>
              </thead>
              <tbody>
                {occupancy.map((occRow) => {
                  const revRow = revenue.find((row) => row.reportDate === occRow.reportDate && row.venueId === occRow.venueId);
                  return (
                    <tr key={`${occRow.reportDate}-${occRow.venueId}`}>
                      <td>{occRow.reportDate}</td>
                      <td>{occRow.totalBookings}</td>
                      <td>{Number(occRow.bookedHours).toFixed(1)}h</td>
                      <td>{formatCurrency(revRow?.bookingRevenue ?? 0)}</td>
                      <td>{formatCurrency(revRow?.depositRevenue ?? 0)}</td>
                      <td>{formatCurrency(revRow?.addOnRevenue ?? 0)}</td>
                    </tr>
                  );
                })}
                {!occupancy.length && (
                  <tr><td colSpan={6} className="muted">Chưa có dữ liệu báo cáo.</td></tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="grid two">
            <article className="card inner-card">
              <h3>Danh sách sân - {selectedVenue?.name ?? "-"}</h3>
              <ul className="list-clean">
                {courts.map((court) => <li key={court.id}>{court.name} ({court.sportType})</li>)}
                {!courts.length && <li className="muted">Chưa có sân</li>}
              </ul>
            </article>
            <article className="card inner-card">
              <h3>Best hours</h3>
              <ul className="list-clean">
                {bestHours.map((row) => (
                  <li key={row.hourOfDay}>{String(row.hourOfDay).padStart(2, "0")}:00 · {row.bookingCount} lượt đặt · {Number(row.bookedHours).toFixed(1)}h</li>
                ))}
                {!bestHours.length && <li className="muted">Chưa có dữ liệu</li>}
              </ul>
            </article>
          </div>

          <article className="card inner-card">
            <h3>Tình trạng pricing rule theo sân</h3>
            <ul className="list-clean">
              {courts.map((court) => (
                <li key={court.id} className="ops-pricing-row">
                  <span>{court.name}</span>
                  <strong>{pricingRuleCountByCourt[court.id] ?? 0} rule</strong>
                </li>
              ))}
              {!courts.length && <li className="muted">Chưa có sân để đánh giá pricing.</li>}
            </ul>
            {missingPricingCourts.length > 0 ? (
              <p className="inline-error">
                Còn {missingPricingCourts.length} sân chưa có pricing rule. Vào trang Pricing Rules để tạo baseline.
              </p>
            ) : (
              <p className="inline-success">Tất cả sân trong cụm này đã có pricing rule.</p>
            )}
          </article>
        </article>

        <article className="card ops-actions">
          <h3>Quick actions</h3>
          <label>Tên venue <input value={venueName} onChange={(e) => setVenueName(e.target.value)} /></label>
          <label>Địa chỉ <input value={venueAddress} onChange={(e) => setVenueAddress(e.target.value)} /></label>
          <button className="btn" onClick={() => { void createVenue(); }} disabled={busy || !canWrite}>Tạo venue</button>

          <hr />

          <h4>Chỉnh sửa venue hiện tại</h4>
          <label>Tên venue
            <input
              value={editVenueName}
              onChange={(e) => setEditVenueName(e.target.value)}
              disabled={!selectedVenueId}
            />
          </label>
          <label>Địa chỉ
            <input
              value={editVenueAddress}
              onChange={(e) => setEditVenueAddress(e.target.value)}
              disabled={!selectedVenueId}
            />
          </label>
          <div className="ops-action-row">
            <button
              className="btn"
              onClick={() => { void updateVenue(); }}
              disabled={busy || !canWrite || !selectedVenueId}
            >
              Lưu thay đổi
            </button>
            <button
              className="btn ghost"
              onClick={() => {
                setEditVenueName(selectedVenue?.name ?? "");
                setEditVenueAddress(selectedVenue?.address ?? "");
              }}
              disabled={busy || !selectedVenueId}
            >
              Hoàn tác
            </button>
          </div>

          <hr />

          <label>Tên sân <input value={courtName} onChange={(e) => setCourtName(e.target.value)} /></label>
          <label>
            Sport
            <select value={sportType} onChange={(e) => setSportType(e.target.value)}>
              {sports.map((sport) => <option key={sport} value={sport}>{sport}</option>)}
            </select>
          </label>
          <button className="btn" onClick={() => { void createCourt(); }} disabled={busy || !canWrite}>Tạo sân</button>

          <hr />

          <label>Tên sản phẩm <input value={productName} onChange={(e) => setProductName(e.target.value)} /></label>
          <label>Đơn giá <input type="number" value={unitPrice} onChange={(e) => setUnitPrice(Number(e.target.value))} /></label>
          <button className="btn" onClick={() => { void createProduct(); }} disabled={busy || !canWrite}>Tạo sản phẩm</button>

          <h4>Products</h4>
          <ul className="list-clean compact-list">
            {products.map((product) => <li key={product.id}>{product.name} · {formatCurrency(product.unitPrice)}</li>)}
            {!products.length && <li className="muted">Chưa có sản phẩm</li>}
          </ul>
        </article>
      </section>

      {(error || notice) && (
        <section className="toast-stack">
          {error && <p className="toast error-text">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p>}
          {notice && <p className="toast success-text">{notice}</p>}
        </section>
      )}
    </main>
  );
}


