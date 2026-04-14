import { Link } from "react-router-dom";
import { getApiBaseUrl } from "../lib/api";

export function LandingPage() {
  return (
    <main className="page page-landing">
      <section className="hero">
        <div>
          <p className="eyebrow">ALOBO-inspired UI flow</p>
          <h1>Hệ thống đặt sân xanh dương cho cả khách hàng và vận hành nội bộ</h1>
          <p>
            Frontend này được dựng theo flow của tài liệu UI tham chiếu: trang khách hàng tập trung booking,
            trang vận hành tập trung lịch, KPI và thao tác nhanh.
          </p>
          <div className="inline-actions">
            <Link to="/customer" className="btn">Đi tới flow khách hàng</Link>
            <Link to="/ops" className="btn ghost">Đi tới flow vận hành</Link>
          </div>
          <p className="muted">API base: <code>{getApiBaseUrl()}</code></p>
        </div>
        <div className="hero-panel">
          <h3>Journey map</h3>
          <ol>
            <li>Khám phá sân và khung giờ trống</li>
            <li>Báo giá và tạo booking draft</li>
            <li>Đặt cọc / xác nhận booking</li>
            <li>Thêm dịch vụ add-on</li>
            <li>Theo dõi occupancy + revenue</li>
          </ol>
        </div>
      </section>

      <section className="grid two">
        <article className="card lift">
          <h3>Customer portal</h3>
          <p>Danh sách sân, availability grid, đặt lịch, quản lý đơn đã đặt, add-on.</p>
          <Link to="/customer" className="text-link">Mở portal</Link>
        </article>

        <article className="card lift">
          <h3>Operations portal</h3>
          <p>Tạo venue/court/product, theo dõi occupancy/revenue/best-hours theo branch.</p>
          <Link to="/ops" className="text-link">Mở portal</Link>
        </article>
      </section>
    </main>
  );
}
