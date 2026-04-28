import { useEffect, useMemo, useState } from "react";
import { toErrorPresentation } from "../../lib/errorPresentation";
import {
  createPricingRule,
  listCourts,
  listPricingRules,
  listVenues,
  type Court,
  type CreatePricingRulePayload,
  type PricingDayType,
  type PricingRule,
  type PricingRuleCustomerTier,
  type Venue,
} from "../../lib/coreApi";

const dayTypeOptions: PricingDayType[] = ["ALL", "WEEKDAY", "WEEKEND"];
const tierOptions: PricingRuleCustomerTier[] = ["ALL", "STANDARD", "MEMBER", "VIP"];

const defaultForm: CreatePricingRulePayload = {
  courtId: "",
  name: "",
  dayType: "WEEKDAY",
  startTime: "05:00",
  endTime: "23:30",
  customerTier: "ALL",
  pricePerHour: 120000,
  priority: 10,
};

function normalizeRuleName(payload: CreatePricingRulePayload, courtName: string) {
  if (payload.name.trim()) {
    return payload.name.trim();
  }
  return `${courtName} ${payload.dayType} ${payload.customerTier} ${payload.startTime}-${payload.endTime}`;
}

export function PricingRuleManagementPage() {
  const [venues, setVenues] = useState<Venue[]>([]);
  const [courts, setCourts] = useState<Court[]>([]);
  const [rules, setRules] = useState<PricingRule[]>([]);
  const [selectedVenueId, setSelectedVenueId] = useState("");
  const [selectedCourtId, setSelectedCourtId] = useState("");
  const [form, setForm] = useState<CreatePricingRulePayload>(defaultForm);

  const [busy, setBusy] = useState(false);
  const [loadingRules, setLoadingRules] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const selectedCourt = useMemo(
    () => courts.find((court) => court.id === selectedCourtId) ?? null,
    [courts, selectedCourtId],
  );

  useEffect(() => {
    async function loadInitialData() {
      try {
        setError(null);
        setTraceId(null);
        const venueRows = await listVenues();
        setVenues(venueRows);
        if (venueRows[0]) {
          setSelectedVenueId(venueRows[0].id);
        }
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được danh sách cụm sân");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      }
    }
    void loadInitialData();
  }, []);

  useEffect(() => {
    async function loadCourtRows() {
      if (!selectedVenueId) {
        setCourts([]);
        setSelectedCourtId("");
        setRules([]);
        return;
      }
      try {
        setError(null);
        setTraceId(null);
        const courtRows = await listCourts(selectedVenueId);
        setCourts(courtRows);
        const nextCourtId = courtRows[0]?.id ?? "";
        setSelectedCourtId((prev) => (prev && courtRows.some((court) => court.id === prev) ? prev : nextCourtId));
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được danh sách sân");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      }
    }
    void loadCourtRows();
  }, [selectedVenueId]);

  useEffect(() => {
    setForm((prev) => ({
      ...prev,
      courtId: selectedCourtId,
    }));
  }, [selectedCourtId]);

  useEffect(() => {
    async function loadPricingRules() {
      if (!selectedCourtId) {
        setRules([]);
        return;
      }
      try {
        setLoadingRules(true);
        setError(null);
        setTraceId(null);
        const rows = await listPricingRules(selectedCourtId);
        setRules(rows);
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được pricing rules");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      } finally {
        setLoadingRules(false);
      }
    }
    void loadPricingRules();
  }, [selectedCourtId]);

  function updateForm<K extends keyof CreatePricingRulePayload>(key: K, value: CreatePricingRulePayload[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  function validateForm(payload: CreatePricingRulePayload): string | null {
    if (!payload.courtId) {
      return "Cần chọn sân để tạo rule.";
    }
    if (payload.pricePerHour <= 0) {
      return "Giá theo giờ phải lớn hơn 0.";
    }
    if (payload.endTime <= payload.startTime) {
      return "Giờ kết thúc phải lớn hơn giờ bắt đầu.";
    }
    return null;
  }

  async function handleCreateRule() {
    const validatedError = validateForm(form);
    if (validatedError) {
      setError(validatedError);
      setTraceId(null);
      return;
    }

    if (!selectedCourt) {
      setError("Không xác định được sân đang chọn.");
      setTraceId(null);
      return;
    }

    try {
      setBusy(true);
      setError(null);
      setTraceId(null);
      setNotice(null);
      await createPricingRule({
        ...form,
        name: normalizeRuleName(form, selectedCourt.name),
        priority: form.priority ?? 0,
      });
      const refreshed = await listPricingRules(selectedCourtId);
      setRules(refreshed);
      setNotice("Đã tạo pricing rule.");
    } catch (err) {
      const uiError = toErrorPresentation(err, "Tạo pricing rule thất bại");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  async function handleCreateBaseline() {
    if (!selectedCourtId || !selectedCourt) {
      setError("Cần chọn sân trước khi tạo baseline.");
      setTraceId(null);
      return;
    }
    try {
      setBusy(true);
      setError(null);
      setTraceId(null);
      setNotice(null);

      await Promise.all([
        createPricingRule({
          courtId: selectedCourtId,
          name: `${selectedCourt.name} WEEKDAY STANDARD`,
          dayType: "WEEKDAY",
          startTime: "05:00",
          endTime: "23:30",
          customerTier: "STANDARD",
          pricePerHour: 120000,
          priority: 20,
        }),
        createPricingRule({
          courtId: selectedCourtId,
          name: `${selectedCourt.name} WEEKEND STANDARD`,
          dayType: "WEEKEND",
          startTime: "05:00",
          endTime: "23:30",
          customerTier: "STANDARD",
          pricePerHour: 140000,
          priority: 20,
        }),
        createPricingRule({
          courtId: selectedCourtId,
          name: `${selectedCourt.name} fallback ALL`,
          dayType: "ALL",
          startTime: "05:00",
          endTime: "23:30",
          customerTier: "ALL",
          pricePerHour: 100000,
          priority: 5,
        }),
      ]);

      const refreshed = await listPricingRules(selectedCourtId);
      setRules(refreshed);
      setNotice("Đã tạo baseline rule (weekday/weekend/fallback).");
    } catch (err) {
      const uiError = toErrorPresentation(err, "Tạo baseline thất bại");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="page">
      <section className="section-header">
        <p className="eyebrow">Admin / Owner</p>
        <h1>Quản lý bảng giá sân (Pricing Rule)</h1>
        <p className="muted">Cấu hình pricing rule theo sân, ngày, khung giờ và tier khách hàng.</p>
      </section>

      <section className="grid two">
        <article className="card">
          <h3>Tạo rule mới</h3>

          <label>
            Cụm sân
            <select value={selectedVenueId} onChange={(event) => setSelectedVenueId(event.target.value)}>
              <option value="">-- chọn cụm sân --</option>
              {venues.map((venue) => (
                <option key={venue.id} value={venue.id}>
                  {venue.name}
                </option>
              ))}
            </select>
          </label>

          <label>
            Sân
            <select value={selectedCourtId} onChange={(event) => setSelectedCourtId(event.target.value)}>
              <option value="">-- chọn sân --</option>
              {courts.map((court) => (
                <option key={court.id} value={court.id}>
                  {court.name} ({court.sportType})
                </option>
              ))}
            </select>
          </label>

          <label>
            Tên rule
            <input
              value={form.name}
              onChange={(event) => updateForm("name", event.target.value)}
              placeholder="Để trống sẽ tự sinh theo mẫu"
            />
          </label>

          <div className="grid two">
            <label>
              Day type
              <select value={form.dayType} onChange={(event) => updateForm("dayType", event.target.value as PricingDayType)}>
                {dayTypeOptions.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </label>

            <label>
              Tier
              <select value={form.customerTier} onChange={(event) => updateForm("customerTier", event.target.value as PricingRuleCustomerTier)}>
                {tierOptions.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <div className="grid two">
            <label>
              Giờ bắt đầu
              <input type="time" step={1800} value={form.startTime} onChange={(event) => updateForm("startTime", event.target.value)} />
            </label>
            <label>
              Giờ kết thúc
              <input type="time" step={1800} value={form.endTime} onChange={(event) => updateForm("endTime", event.target.value)} />
            </label>
          </div>

          <div className="grid two">
            <label>
              Giá/giờ (VND)
              <input
                type="number"
                min={1000}
                step={1000}
                value={form.pricePerHour}
                onChange={(event) => updateForm("pricePerHour", Number(event.target.value))}
              />
            </label>
            <label>
              Priority
              <input
                type="number"
                value={form.priority ?? 0}
                onChange={(event) => updateForm("priority", Number(event.target.value))}
              />
            </label>
          </div>

          <div className="ops-toolbar">
            <button className="btn" type="button" onClick={() => { void handleCreateRule(); }} disabled={busy}>
              Tạo rule
            </button>
            <button className="btn ghost" type="button" onClick={() => { void handleCreateBaseline(); }} disabled={busy || !selectedCourtId}>
              Tạo baseline nhanh
            </button>
          </div>
        </article>

        <article className="card">
          <h3>Danh sách rule hiện có</h3>
          {loadingRules ? <p className="muted">Đang tải pricing rules...</p> : null}
          <div className="ops-table-wrap">
            <table className="ops-table">
              <thead>
                <tr>
                  <th>Tên</th>
                  <th>Day</th>
                  <th>Tier</th>
                  <th>Khung giờ</th>
                  <th>Giá/giờ</th>
                  <th>Priority</th>
                </tr>
              </thead>
              <tbody>
                {rules.map((rule) => (
                  <tr key={rule.id}>
                    <td>{rule.name}</td>
                    <td>{rule.dayType}</td>
                    <td>{rule.customerTier}</td>
                    <td>{rule.startTime} - {rule.endTime}</td>
                    <td>{Number(rule.pricePerHour).toLocaleString("vi-VN")} đ</td>
                    <td>{rule.priority}</td>
                  </tr>
                ))}
                {!rules.length && !loadingRules ? (
                  <tr>
                    <td colSpan={6} className="muted">Chưa có pricing rule cho sân này.</td>
                  </tr>
                ) : null}
              </tbody>
            </table>
          </div>
        </article>
      </section>

      {error ? <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p> : null}
      {notice ? <p className="inline-success">{notice}</p> : null}
    </main>
  );
}

