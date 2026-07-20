import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  OPS_PRICING_ROLES,
} from "../../app/routeRolePolicy";
import { apiFetch, createIdempotencyKey, formatCurrency } from "../../lib/api";
import { useAuth } from "../../context/AuthContext";
import { toErrorPresentation } from "../../lib/errorPresentation";
import {
  buildDateRangeIso,
  cancelBooking,
  createVenueImage,
  confirmBooking,
  deleteVenueImage,
  listBookings,
  listPricingRules,
  listVenueImages,
  rescheduleBooking,
  setVenueCoverImage,
  type Booking,
  type VenueImage,
} from "../../lib/coreApi";
import { BookingDetailDrawer } from "./BookingDetailDrawer";
import { CourtScheduleTimeline } from "./CourtScheduleTimeline";
import { TodayBookingsPanel } from "./TodayBookingsPanel";

type Venue = {
  id: string;
  name: string;
  address: string;
  description?: string | null;
  coverImageUrl?: string | null;
  imageUrl?: string | null;
  phone?: string | null;
  openingTime?: string | null;
  closingTime?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  images?: VenueImage[];
};
type Court = { id: string; venueId: string; name: string; sportType: string };
type Product = {
  id: string;
  venueId: string;
  name: string;
  description?: string | null;
  imageUrl?: string | null;
  category?: string | null;
  unit?: string | null;
  unitPrice: number;
  active: boolean;
};
type ReportPage<T> = { items: T[]; totalElements: number; totalPages: number };
type OccupancyRow = { reportDate: string; venueId: string; totalBookings: number; bookedHours: number };
type RevenueRow = { reportDate: string; venueId: string; bookingRevenue: number; depositRevenue: number; addOnRevenue: number; totalRevenue: number };
type BestHourRow = { hourOfDay: number; bookingCount: number; bookedHours: number };
type DashboardSubTab = "overview" | "venues" | "courts" | "addons" | "pricing";

const sports = ["BADMINTON", "PICKLEBALL", "FOOTBALL"];
const dashboardSubTabValues: DashboardSubTab[] = ["overview", "venues", "courts", "addons", "pricing"];

function isDashboardSubTab(value: string | null): value is DashboardSubTab {
  return dashboardSubTabValues.includes(value as DashboardSubTab);
}

export function OpsPortalPage() {
  const { token, hasAnyRole, isAuthenticated } = useAuth();
  const [searchParams] = useSearchParams();
  const [venues, setVenues] = useState<Venue[]>([]);
  const [courts, setCourts] = useState<Court[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [venueImages, setVenueImages] = useState<VenueImage[]>([]);

  const [venueName, setVenueName] = useState("");
  const [venueAddress, setVenueAddress] = useState("");
  const [venueDescription, setVenueDescription] = useState("");
  const [venueCoverImageUrl, setVenueCoverImageUrl] = useState("");
  const [venuePhone, setVenuePhone] = useState("");
  const [venueOpeningTime, setVenueOpeningTime] = useState("");
  const [venueClosingTime, setVenueClosingTime] = useState("");
  const [venueLatitude, setVenueLatitude] = useState("");
  const [venueLongitude, setVenueLongitude] = useState("");
  const [editVenueName, setEditVenueName] = useState("");
  const [editVenueAddress, setEditVenueAddress] = useState("");
  const [editVenueDescription, setEditVenueDescription] = useState("");
  const [editVenueCoverImageUrl, setEditVenueCoverImageUrl] = useState("");
  const [editVenuePhone, setEditVenuePhone] = useState("");
  const [editVenueOpeningTime, setEditVenueOpeningTime] = useState("");
  const [editVenueClosingTime, setEditVenueClosingTime] = useState("");
  const [editVenueLatitude, setEditVenueLatitude] = useState("");
  const [editVenueLongitude, setEditVenueLongitude] = useState("");
  const [selectedVenueId, setSelectedVenueId] = useState("");
  const [courtName, setCourtName] = useState("");
  const [sportType, setSportType] = useState(sports[0]);
  const [productName, setProductName] = useState("");
  const [productDescription, setProductDescription] = useState("");
  const [productImageUrl, setProductImageUrl] = useState("");
  const [productCategory, setProductCategory] = useState("");
  const [productUnit, setProductUnit] = useState("");
  const [unitPrice, setUnitPrice] = useState<number>(25000);
  const [venueGalleryImageUrl, setVenueGalleryImageUrl] = useState("");
  const [venueGalleryAltText, setVenueGalleryAltText] = useState("");
  const [venueGallerySortOrder, setVenueGallerySortOrder] = useState("0");

  const [fromDate, setFromDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [toDate, setToDate] = useState(() => new Date(Date.now() + 86400000).toISOString().slice(0, 10));
  const [operatingDate, setOperatingDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [occupancy, setOccupancy] = useState<OccupancyRow[]>([]);
  const [revenue, setRevenue] = useState<RevenueRow[]>([]);
  const [bestHours, setBestHours] = useState<BestHourRow[]>([]);
  const [pricingRuleCountByCourt, setPricingRuleCountByCourt] = useState<Record<string, number>>({});
  const [todayBookings, setTodayBookings] = useState<Booking[]>([]);
  const [selectedBooking, setSelectedBooking] = useState<Booking | null>(null);
  const [isCreateVenueModalOpen, setIsCreateVenueModalOpen] = useState(false);
  const [isEditVenueModalOpen, setIsEditVenueModalOpen] = useState(false);
  const [isCreateCourtModalOpen, setIsCreateCourtModalOpen] = useState(false);
  const [isCreateProductModalOpen, setIsCreateProductModalOpen] = useState(false);

  const [busy, setBusy] = useState(false);
  const [opsScheduleLoading, setOpsScheduleLoading] = useState(false);
  const [bookingActionBusy, setBookingActionBusy] = useState(false);
  const [pricingHealthLoading, setPricingHealthLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const canWrite = isAuthenticated;
  const selectedVenue = useMemo(() => venues.find((item) => item.id === selectedVenueId) ?? null, [venues, selectedVenueId]);
  const tabParam = searchParams.get("tab");
  const activeDashboardSubTab: DashboardSubTab = isDashboardSubTab(tabParam) ? tabParam : "overview";

  const loadOperatingBookings = useCallback(async () => {
    if (!courts.length) {
      setTodayBookings([]);
      return;
    }

    try {
      setOpsScheduleLoading(true);
      setError(null);
      setTraceId(null);
      const { from, to } = buildDateRangeIso(operatingDate);
      const pages = await Promise.all(
        courts.map((court) => listBookings({ courtId: court.id, from, to, page: 0, size: 100 })),
      );
      const rows = pages
        .flatMap((page) => page.items)
        .sort((a, b) => new Date(a.startTime).getTime() - new Date(b.startTime).getTime());
      setTodayBookings(rows);
    } catch (err) {
      const uiError = toErrorPresentation(err, "Không tải được lịch vận hành");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setOpsScheduleLoading(false);
    }
  }, [courts, operatingDate]);

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
      const imageRows = await listVenueImages(venueId);
      setCourts(courtRows);
      setProducts(productRows);
      setVenueImages(imageRows);
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
      setVenueImages([]);
      setPricingRuleCountByCourt({});
      setTodayBookings([]);
      return;
    }
    void loadVenueData(selectedVenueId);
  }, [loadVenueData, selectedVenueId]);

  useEffect(() => {
    void loadOperatingBookings();
  }, [loadOperatingBookings]);

  useEffect(() => {
    if (!selectedBooking) {
      return;
    }
    const refreshedBooking = todayBookings.find((booking) => booking.id === selectedBooking.id) ?? null;
    setSelectedBooking(refreshedBooking);
  }, [selectedBooking, todayBookings]);

  useEffect(() => {
    setEditVenueName(selectedVenue?.name ?? "");
    setEditVenueAddress(selectedVenue?.address ?? "");
    setEditVenueDescription(selectedVenue?.description ?? "");
    setEditVenueCoverImageUrl(selectedVenue?.coverImageUrl ?? selectedVenue?.imageUrl ?? "");
    setEditVenuePhone(selectedVenue?.phone ?? "");
    setEditVenueOpeningTime((selectedVenue?.openingTime ?? "").slice(0, 5));
    setEditVenueClosingTime((selectedVenue?.closingTime ?? "").slice(0, 5));
    setEditVenueLatitude(selectedVenue?.latitude == null ? "" : String(selectedVenue.latitude));
    setEditVenueLongitude(selectedVenue?.longitude == null ? "" : String(selectedVenue.longitude));
  }, [selectedVenue]);

  function optionalNumber(value: string): number | null {
    const trimmed = value.trim();
    if (!trimmed) {
      return null;
    }
    const numeric = Number(trimmed);
    return Number.isFinite(numeric) ? numeric : null;
  }

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
      const created = await apiFetch<Venue>("/api/core/venues", {
        method: "POST",
        headers: {
          "Idempotency-Key": createIdempotencyKey("ops-venue-create"),
        },
        body: JSON.stringify({
          name: venueName,
          address: venueAddress,
          description: venueDescription,
          coverImageUrl: venueCoverImageUrl,
          phone: venuePhone,
          openingTime: venueOpeningTime || null,
          closingTime: venueClosingTime || null,
          latitude: optionalNumber(venueLatitude),
          longitude: optionalNumber(venueLongitude),
        }),
      }, token.accessToken);
      setVenueName("");
      setVenueAddress("");
      setVenueDescription("");
      setVenueCoverImageUrl("");
      setVenuePhone("");
      setVenueOpeningTime("");
      setVenueClosingTime("");
      setVenueLatitude("");
      setVenueLongitude("");
      setSelectedVenueId(created.id);
      await loadVenues();
      setIsCreateVenueModalOpen(false);
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
        body: JSON.stringify({
          name: nextName,
          address: nextAddress,
          description: editVenueDescription,
          coverImageUrl: editVenueCoverImageUrl,
          phone: editVenuePhone,
          openingTime: editVenueOpeningTime || null,
          closingTime: editVenueClosingTime || null,
          latitude: optionalNumber(editVenueLatitude),
          longitude: optionalNumber(editVenueLongitude),
        }),
      }, token.accessToken);
      setVenues((current) => current.map((venue) => (venue.id === updated.id ? updated : venue)));
      await loadVenues();
      setIsEditVenueModalOpen(false);
      setNotice("Đã cập nhật thông tin venue");
    } catch (err) {
      const uiError = toErrorPresentation(err, "Cập nhật venue thất bại");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  async function createVenueGalleryImage() {
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
      await createVenueImage(selectedVenueId, {
        imageUrl: venueGalleryImageUrl,
        altText: venueGalleryAltText,
        sortOrder: optionalNumber(venueGallerySortOrder) ?? 0,
      }, token.accessToken);
      setVenueGalleryImageUrl("");
      setVenueGalleryAltText("");
      setVenueGallerySortOrder("0");
      await loadVenueData(selectedVenueId);
      await loadVenues();
      setNotice("Đã thêm ảnh sân");
    } catch (err) {
      const uiError = toErrorPresentation(err, "Thêm ảnh sân thất bại");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  async function deleteVenueGalleryImage(imageId: string) {
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
      await deleteVenueImage(selectedVenueId, imageId, token.accessToken);
      await loadVenueData(selectedVenueId);
      await loadVenues();
      setNotice("Đã xóa ảnh sân");
    } catch (err) {
      const uiError = toErrorPresentation(err, "Xóa ảnh sân thất bại");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  async function setVenueGalleryCover(imageId: string) {
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
      await setVenueCoverImage(selectedVenueId, imageId, token.accessToken);
      await loadVenueData(selectedVenueId);
      await loadVenues();
      setNotice("Đã đặt ảnh đại diện");
    } catch (err) {
      const uiError = toErrorPresentation(err, "Đặt ảnh đại diện thất bại");
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
      setIsCreateCourtModalOpen(false);
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
        body: JSON.stringify({
          venueId: selectedVenueId,
          name: productName,
          description: productDescription,
          imageUrl: productImageUrl,
          category: productCategory,
          unit: productUnit,
          unitPrice,
          active: true,
        }),
      }, token.accessToken);
      setProductName("");
      setProductDescription("");
      setProductImageUrl("");
      setProductCategory("");
      setProductUnit("");
      await loadVenueData(selectedVenueId);
      setIsCreateProductModalOpen(false);
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

  async function handleBookingAction(
    action: "confirm" | "cancel" | "reschedule",
    booking: Booking,
    payload?: { courtId?: string; startTime: string; endTime: string },
  ) {
    try {
      setBookingActionBusy(true);
      setError(null);
      setTraceId(null);
      setNotice(null);
      if (action === "confirm") {
        await confirmBooking(booking.id);
        setNotice("Đã xác nhận booking");
      } else if (action === "cancel") {
        await cancelBooking(booking.id);
        setNotice("Đã hủy booking");
      } else if (payload) {
        await rescheduleBooking(booking.id, payload);
        setNotice("Đã đổi lịch booking");
      }
      await loadOperatingBookings();
    } catch (err) {
      const uiError = toErrorPresentation(err, "Thao tác booking thất bại");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBookingActionBusy(false);
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
      <section className="card ops-dashboard-filter-card">
        <label>Cụm sân
          <select value={selectedVenueId} onChange={(e) => setSelectedVenueId(e.target.value)}>
            <option value="">-- chọn venue --</option>
            {venues.map((venue) => <option key={venue.id} value={venue.id}>{venue.name}</option>)}
          </select>
        </label>
        <label>Từ ngày <input type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)} /></label>
        <label>Đến ngày <input type="date" value={toDate} onChange={(e) => setToDate(e.target.value)} /></label>
        <button className="btn" onClick={() => { void loadReports(); }} disabled={busy}>Tải báo cáo</button>
      </section>

      {activeDashboardSubTab === "overview" && (
        <>
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

          <section className="ops-operating-section">
            <article className="card ops-operating-toolbar">
              <div>
                <p className="eyebrow">Daily operations</p>
                <h3>Lịch vận hành thực tế</h3>
              </div>
              <label>Ngày vận hành
                <input type="date" value={operatingDate} onChange={(e) => setOperatingDate(e.target.value)} />
              </label>
              <button className="btn" onClick={() => { void loadOperatingBookings(); }} disabled={opsScheduleLoading}>
                Refresh lịch
              </button>
            </article>
            <div className="ops-operating-grid">
              <CourtScheduleTimeline
                courts={courts}
                bookings={todayBookings}
                date={operatingDate}
                loading={opsScheduleLoading}
                onSelectBooking={setSelectedBooking}
              />
              <TodayBookingsPanel
                courts={courts}
                bookings={todayBookings}
                loading={opsScheduleLoading}
                onSelectBooking={setSelectedBooking}
              />
            </div>
          </section>

          <section className="card ops-main">
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
                  {courts.slice(0, 6).map((court) => <li key={court.id}>{court.name} ({court.sportType})</li>)}
                  {courts.length > 6 && <li className="muted">+{courts.length - 6} sân khác trong tab Sân</li>}
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
          </section>
        </>
      )}

      {activeDashboardSubTab === "venues" && (
        <section className="ops-tab-grid">
          <article className="card ops-form-card ops-venue-actions-card">
            <div>
              <p className="eyebrow">Venue operations</p>
              <h3>Quản lý cụm sân</h3>
              <p className="muted">Tạo mới hoặc cập nhật cụm sân bằng modal để giữ dashboard gọn.</p>
            </div>
            <div className="ops-venue-action-buttons">
              <button
                className="btn"
                onClick={() => {
                  setError(null);
                  setTraceId(null);
                  setIsCreateVenueModalOpen(true);
                }}
                disabled={busy || !canWrite}
              >
                Tạo mới cụm sân
              </button>
              <button
                className="btn ghost"
                onClick={() => {
                  setError(null);
                  setTraceId(null);
                  setIsEditVenueModalOpen(true);
                }}
                disabled={busy || !canWrite || !selectedVenueId}
              >
                Chỉnh sửa cụm sân hiện tại
              </button>
            </div>
            {!selectedVenueId ? <p className="inline-muted">Chọn cụm sân trước khi chỉnh sửa.</p> : null}
          </article>

          <article className="card ops-form-card ops-venue-summary-card">
            <h3>Thông tin cụm sân {selectedVenue?.name ?? ""}</h3>
            {selectedVenue?.coverImageUrl || selectedVenue?.imageUrl ? (
              <OpsImagePreview className="ops-metadata-image" src={selectedVenue.coverImageUrl ?? selectedVenue.imageUrl ?? ""} alt={selectedVenue.name} />
            ) : null}
            <ul className="list-clean">
              <li>Địa chỉ: <strong>{selectedVenue?.address ?? "-"}</strong></li>
              <li>Mô tả: <strong>{selectedVenue?.description || "-"}</strong></li>
              <li>Điện thoại: <strong>{selectedVenue?.phone || "-"}</strong></li>
              <li>Giờ hoạt động: <strong>{selectedVenue?.openingTime || selectedVenue?.closingTime ? `${selectedVenue.openingTime ?? "--:--"} - ${selectedVenue.closingTime ?? "--:--"}` : "-"}</strong></li>
              <li>Tổng số sân: <strong>{courts.length}</strong></li>
              {/* <li>Dịch vụ/add-on: <strong>{products.length}</strong></li> */}
            </ul>
          </article>

          <article className="card ops-form-card ops-gallery-card">
            <h3>Thư viện ảnh sân</h3>
            <label>Image URL <input value={venueGalleryImageUrl} onChange={(e) => setVenueGalleryImageUrl(e.target.value)} placeholder="https://..." /></label>
            <div className="ops-form-row">
              <label>Alt text <input value={venueGalleryAltText} onChange={(e) => setVenueGalleryAltText(e.target.value)} /></label>
              <label>Sort order <input type="number" value={venueGallerySortOrder} onChange={(e) => setVenueGallerySortOrder(e.target.value)} /></label>
            </div>
            <button className="btn" onClick={() => { void createVenueGalleryImage(); }} disabled={busy || !canWrite || !selectedVenueId}>Thêm ảnh</button>

            <div className="ops-gallery-grid">
              {venueImages.map((image) => (
                <article key={image.id} className="ops-gallery-item">
                  <OpsImagePreview src={image.imageUrl} alt={image.altText || selectedVenue?.name || "Venue image"} />
                  <div>
                    <strong>{image.altText || "Ảnh sân"}</strong>
                    <small>Sort {image.sortOrder}</small>
                  </div>
                  {image.cover ? <span className="ops-cover-badge">Ảnh đại diện</span> : null}
                  <div className="ops-gallery-actions">
                    <button className="btn ghost" onClick={() => { void setVenueGalleryCover(image.id); }} disabled={busy || image.cover || !canWrite}>Đặt làm ảnh đại diện</button>
                    <button className="btn danger" onClick={() => { void deleteVenueGalleryImage(image.id); }} disabled={busy || !canWrite}>Xóa ảnh</button>
                  </div>
                </article>
              ))}
              {!venueImages.length && <p className="muted">Chưa có ảnh trong thư viện.</p>}
            </div>
          </article>
        </section>
      )}

      {activeDashboardSubTab === "courts" && (
        <section className="ops-tab-grid">
          <article className="card ops-form-card ops-venue-actions-card">
            <div>
              <p className="eyebrow">Court operations</p>
              <h3>Quản lý sân</h3>
              <p className="muted">Tạo sân cho cụm: <strong>{selectedVenue?.name ?? "Chưa chọn"}</strong></p>
            </div>
            <div className="ops-venue-action-buttons">
              <button
                className="btn"
                onClick={() => {
                  setError(null);
                  setTraceId(null);
                  setIsCreateCourtModalOpen(true);
                }}
                disabled={busy || !canWrite || !selectedVenueId}
              >
                Tạo sân
              </button>
            </div>
            {!selectedVenueId ? <p className="inline-muted">Chọn cụm sân trước khi tạo sân.</p> : null}
            <p className="inline-muted">Chưa có API update/toggle active court trong backend, nên tab này chỉ tạo và liệt kê sân.</p>
          </article>

          <article className="card ops-form-card ops-list-card">
            <h3>Danh sách sân - {selectedVenue?.name ?? "-"}</h3>
            <ul className="list-clean">
              {courts.map((court) => (
                <li key={court.id} className="ops-pricing-row">
                  <span>{court.name}</span>
                  <strong>{court.sportType}</strong>
                </li>
              ))}
              {!courts.length && <li className="muted">Chưa có sân</li>}
            </ul>
          </article>
        </section>
      )}

      {activeDashboardSubTab === "addons" && (
        <section className="ops-tab-grid">
          <article className="card ops-form-card ops-venue-actions-card">
            <div>
              <p className="eyebrow">Add-on operations</p>
              <h3>Quản lý dịch vụ / Add-on</h3>
              <p className="muted">Tạo product cho cụm: <strong>{selectedVenue?.name ?? "Chưa chọn"}</strong></p>
            </div>
            <div className="ops-venue-action-buttons">
              <button
                className="btn"
                onClick={() => {
                  setError(null);
                  setTraceId(null);
                  setIsCreateProductModalOpen(true);
                }}
                disabled={busy || !canWrite || !selectedVenueId}
              >
                Tạo dịch vụ / Add-on
              </button>
            </div>
            {!selectedVenueId ? <p className="inline-muted">Chọn cụm sân trước khi tạo dịch vụ.</p> : null}
            <p className="inline-muted">Backend hiện chỉ có API tạo/list product, chưa có update/toggle product.</p>
          </article>

          <article className="card ops-form-card ops-list-card">
            <h3>Product / Add-on hiện có</h3>
            <ul className="list-clean compact-list">
              {products.map((product) => (
                <li key={product.id} className="ops-product-row">
                  {product.imageUrl ? <img src={product.imageUrl} alt={product.name} /> : <span className="ops-product-placeholder" aria-hidden="true" />}
                  <span>
                    <strong>{product.name}</strong>
                    {product.description ? <small>{product.description}</small> : null}
                    <small>{[product.category, product.unit].filter(Boolean).join(" · ") || "Chưa phân loại"}</small>
                  </span>
                  <b>{formatCurrency(product.unitPrice)}</b>
                </li>
              ))}
              {!products.length && <li className="muted">Chưa có sản phẩm</li>}
            </ul>
          </article>
        </section>
      )}

      {activeDashboardSubTab === "pricing" && (
        <section className="ops-tab-grid">
          <article className="card ops-form-card">
            <p className="eyebrow">Pricing status summary</p>
            <h3>Tổng quan pricing setup</h3>
            <ul className="list-clean">
              <li>Tỉ lệ phủ pricing: <strong>{pricingCoveragePercent}%</strong></li>
              <li>Sân đã có rule: <strong>{pricingCoveredCourtCount}/{courts.length || 0}</strong></li>
              <li>Sân thiếu rule: <strong>{missingPricingCourts.length}</strong></li>
            </ul>
          </article>

          <article className="card ops-form-card ops-list-card">
            <div className="ops-section-head">
              <div>
                <p className="eyebrow">Court pricing coverage</p>
                <h3>Danh sách sân và trạng thái pricing rule</h3>
              </div>
            </div>
            <ul className="list-clean">
              {courts.map((court) => {
                const count = pricingRuleCountByCourt[court.id] ?? 0;
                return (
                  <li key={court.id} className={count > 0 ? "ops-pricing-row" : "ops-pricing-row ops-pricing-row--missing"}>
                    <span>{court.name}</span>
                    <strong>{count} rule</strong>
                  </li>
                );
              })}
              {!courts.length && <li className="muted">Chưa có sân để đánh giá pricing.</li>}
            </ul>
          </article>

          <article className="card ops-form-card">
            <p className="eyebrow">Missing pricing warnings</p>
            <h3>Cảnh báo pricing</h3>
            {missingPricingCourts.length > 0 ? (
              <p className="inline-error">
                Còn {missingPricingCourts.length} sân chưa có pricing rule. Vào trang Pricing Rules để tạo baseline.
              </p>
            ) : (
              <p className="inline-success">Tất cả sân trong cụm này đã có pricing rule.</p>
            )}
          </article>

          <article className="card ops-form-card">
            <p className="eyebrow">Pricing rules page</p>
            <h3>Thiết lập bảng giá chi tiết</h3>
            <p className="muted">Quản lý rule theo sân, khung giờ, loại ngày và nhóm khách tại trang Pricing Rules.</p>
            {hasAnyRole(OPS_PRICING_ROLES) ? <Link className="btn ghost" to="/ops/pricing-rules">Mở Pricing Rules</Link> : null}
          </article>
        </section>
      )}

      {(error || notice) && (
        <section className="toast-stack">
          {error && <p className="toast error-text">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p>}
          {notice && <p className="toast success-text">{notice}</p>}
        </section>
      )}

      {isCreateVenueModalOpen && (
        <div className="ops-modal-root" role="presentation">
          <button type="button" className="ops-modal-backdrop" onClick={() => setIsCreateVenueModalOpen(false)} aria-label="Đóng modal tạo cụm sân" />
          <section className="ops-modal-panel" role="dialog" aria-modal="true" aria-label="Tạo mới cụm sân">
            <header className="ops-modal-header">
              <h3>Tạo mới cụm sân</h3>
              <button type="button" className="icon-btn" onClick={() => setIsCreateVenueModalOpen(false)} aria-label="Đóng">×</button>
            </header>
            {error ? <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p> : null}
            <div className="ops-modal-body">
              <label>Tên cụm sân <input value={venueName} onChange={(e) => setVenueName(e.target.value)} /></label>
              <label>Địa chỉ <input value={venueAddress} onChange={(e) => setVenueAddress(e.target.value)} /></label>
              <label>Mô tả <textarea value={venueDescription} onChange={(e) => setVenueDescription(e.target.value)} rows={3} /></label>
              <label>Ảnh đại diện / coverImageUrl <input value={venueCoverImageUrl} onChange={(e) => setVenueCoverImageUrl(e.target.value)} placeholder="https://..." /></label>
              <label>Số điện thoại <input value={venuePhone} onChange={(e) => setVenuePhone(e.target.value)} /></label>
              <div className="ops-form-row">
                <label>Giờ mở cửa <input type="time" value={venueOpeningTime} onChange={(e) => setVenueOpeningTime(e.target.value)} /></label>
                <label>Giờ đóng cửa <input type="time" value={venueClosingTime} onChange={(e) => setVenueClosingTime(e.target.value)} /></label>
              </div>
              <div className="ops-form-row">
                <label>Latitude <input type="number" step="0.0000001" value={venueLatitude} onChange={(e) => setVenueLatitude(e.target.value)} /></label>
                <label>Longitude <input type="number" step="0.0000001" value={venueLongitude} onChange={(e) => setVenueLongitude(e.target.value)} /></label>
              </div>
            </div>
            <footer className="ops-modal-actions">
              <button type="button" className="btn ghost" onClick={() => setIsCreateVenueModalOpen(false)} disabled={busy}>Hủy</button>
              <button type="button" className="btn" onClick={() => { void createVenue(); }} disabled={busy || !canWrite}>
                Tạo cụm sân
              </button>
            </footer>
          </section>
        </div>
      )}

      {isEditVenueModalOpen && (
        <div className="ops-modal-root" role="presentation">
          <button type="button" className="ops-modal-backdrop" onClick={() => setIsEditVenueModalOpen(false)} aria-label="Đóng modal chỉnh sửa cụm sân" />
          <section className="ops-modal-panel" role="dialog" aria-modal="true" aria-label="Chỉnh sửa cụm sân">
            <header className="ops-modal-header">
              <h3>Chỉnh sửa cụm sân</h3>
              <button type="button" className="icon-btn" onClick={() => setIsEditVenueModalOpen(false)} aria-label="Đóng">×</button>
            </header>
            {error ? <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p> : null}
            <div className="ops-modal-body">
              <label>Tên cụm sân
                <input value={editVenueName} onChange={(e) => setEditVenueName(e.target.value)} disabled={!selectedVenueId} />
              </label>
              <label>Địa chỉ
                <input value={editVenueAddress} onChange={(e) => setEditVenueAddress(e.target.value)} disabled={!selectedVenueId} />
              </label>
              <label>Mô tả
                <textarea value={editVenueDescription} onChange={(e) => setEditVenueDescription(e.target.value)} disabled={!selectedVenueId} rows={3} />
              </label>
              <label>Ảnh đại diện / coverImageUrl
                <input value={editVenueCoverImageUrl} onChange={(e) => setEditVenueCoverImageUrl(e.target.value)} disabled={!selectedVenueId} placeholder="https://..." />
              </label>
              <label>Số điện thoại
                <input value={editVenuePhone} onChange={(e) => setEditVenuePhone(e.target.value)} disabled={!selectedVenueId} />
              </label>
              <div className="ops-form-row">
                <label>Giờ mở cửa
                  <input type="time" value={editVenueOpeningTime} onChange={(e) => setEditVenueOpeningTime(e.target.value)} disabled={!selectedVenueId} />
                </label>
                <label>Giờ đóng cửa
                  <input type="time" value={editVenueClosingTime} onChange={(e) => setEditVenueClosingTime(e.target.value)} disabled={!selectedVenueId} />
                </label>
              </div>
              <div className="ops-form-row">
                <label>Latitude
                  <input type="number" step="0.0000001" value={editVenueLatitude} onChange={(e) => setEditVenueLatitude(e.target.value)} disabled={!selectedVenueId} />
                </label>
                <label>Longitude
                  <input type="number" step="0.0000001" value={editVenueLongitude} onChange={(e) => setEditVenueLongitude(e.target.value)} disabled={!selectedVenueId} />
                </label>
              </div>
            </div>
            <footer className="ops-modal-actions">
              <button type="button" className="btn ghost" onClick={() => setIsEditVenueModalOpen(false)} disabled={busy}>Hủy</button>
              <button type="button" className="btn" onClick={() => { void updateVenue(); }} disabled={busy || !canWrite || !selectedVenueId}>
                Lưu thay đổi
              </button>
            </footer>
          </section>
        </div>
      )}

      {isCreateCourtModalOpen && (
        <div className="ops-modal-root" role="presentation">
          <button type="button" className="ops-modal-backdrop" onClick={() => setIsCreateCourtModalOpen(false)} aria-label="Đóng modal tạo sân" />
          <section className="ops-modal-panel ops-modal-panel--compact" role="dialog" aria-modal="true" aria-label="Tạo sân">
            <header className="ops-modal-header">
              <div>
                <p className="eyebrow">Court</p>
                <h3>Tạo sân</h3>
              </div>
              <button type="button" className="icon-btn" onClick={() => setIsCreateCourtModalOpen(false)} aria-label="Đóng">×</button>
            </header>
            {error ? <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p> : null}
            <div className="ops-modal-body">
              <p className="muted">Tạo court cho cụm sân: <strong>{selectedVenue?.name ?? "Chưa chọn"}</strong></p>
              <label>Tên sân <input value={courtName} onChange={(e) => setCourtName(e.target.value)} /></label>
              <label>
                Sport
                <select value={sportType} onChange={(e) => setSportType(e.target.value)}>
                  {sports.map((sport) => <option key={sport} value={sport}>{sport}</option>)}
                </select>
              </label>
            </div>
            <footer className="ops-modal-actions">
              <button type="button" className="btn ghost" onClick={() => setIsCreateCourtModalOpen(false)} disabled={busy}>Hủy</button>
              <button type="button" className="btn" onClick={() => { void createCourt(); }} disabled={busy || !canWrite || !selectedVenueId}>
                Tạo sân
              </button>
            </footer>
          </section>
        </div>
      )}

      {isCreateProductModalOpen && (
        <div className="ops-modal-root" role="presentation">
          <button type="button" className="ops-modal-backdrop" onClick={() => setIsCreateProductModalOpen(false)} aria-label="Đóng modal tạo dịch vụ" />
          <section className="ops-modal-panel" role="dialog" aria-modal="true" aria-label="Tạo dịch vụ / Add-on">
            <header className="ops-modal-header">
              <div>
                <p className="eyebrow">Add-on</p>
                <h3>Tạo dịch vụ / Add-on</h3>
              </div>
              <button type="button" className="icon-btn" onClick={() => setIsCreateProductModalOpen(false)} aria-label="Đóng">×</button>
            </header>
            {error ? <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p> : null}
            <div className="ops-modal-body">
              <p className="muted">Tạo product cho cụm sân: <strong>{selectedVenue?.name ?? "Chưa chọn"}</strong></p>
              <label>Tên sản phẩm <input value={productName} onChange={(e) => setProductName(e.target.value)} /></label>
              <label>Mô tả <textarea value={productDescription} onChange={(e) => setProductDescription(e.target.value)} rows={3} /></label>
              <label>Image URL <input value={productImageUrl} onChange={(e) => setProductImageUrl(e.target.value)} placeholder="https://..." /></label>
              <div className="ops-form-row">
                <label>Category <input value={productCategory} onChange={(e) => setProductCategory(e.target.value)} placeholder="drink, rental..." /></label>
                <label>Unit <input value={productUnit} onChange={(e) => setProductUnit(e.target.value)} placeholder="chai, giờ, phần..." /></label>
              </div>
              <label>Đơn giá <input type="number" value={unitPrice} onChange={(e) => setUnitPrice(Number(e.target.value))} /></label>
            </div>
            <footer className="ops-modal-actions">
              <button type="button" className="btn ghost" onClick={() => setIsCreateProductModalOpen(false)} disabled={busy}>Hủy</button>
              <button type="button" className="btn" onClick={() => { void createProduct(); }} disabled={busy || !canWrite || !selectedVenueId}>
                Tạo sản phẩm
              </button>
            </footer>
          </section>
        </div>
      )}

      <BookingDetailDrawer
        open={Boolean(selectedBooking)}
        booking={selectedBooking}
        courts={courts}
        busy={bookingActionBusy}
        onClose={() => setSelectedBooking(null)}
        onAction={handleBookingAction}
      />
    </main>
  );
}

function OpsImagePreview({ src, alt, className = "" }: { src: string; alt: string; className?: string }) {
  const [failed, setFailed] = useState(false);

  if (failed) {
    return (
      <div className={`${className} ops-image-fallback`}>
        <strong>Không tải được ảnh</strong>
        <small>URL cần trỏ trực tiếp tới file ảnh, ví dụ .jpg, .png hoặc .webp.</small>
        <a href={src} target="_blank" rel="noreferrer">Mở URL</a>
      </div>
    );
  }

  return <img className={className} src={src} alt={alt} onError={() => setFailed(true)} />;
}
