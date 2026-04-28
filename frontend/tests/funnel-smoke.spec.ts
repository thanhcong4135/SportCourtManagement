import { expect, test } from "@playwright/test";

const AUTH_STORAGE_KEY = "sportcourt.frontend.auth";
const API_BASE = "http://localhost:8080";

type VenueDto = { id: string; name?: string };
type CourtDto = { id: string; venueId?: string; name?: string; sportType?: string };
type PricingRuleDto = {
  courtId?: string;
  dayType?: "ALL" | "WEEKDAY" | "WEEKEND";
  customerTier?: "ALL" | "STANDARD" | "MEMBER" | "VIP";
  startTime?: string;
  endTime?: string;
  active?: boolean;
};

type DiscoverSeed = {
  keyword: string;
  sport: "PICKLEBALL" | "BADMINTON" | "TENNIS";
  date: string;
  time: string;
  venueId: string;
  courtId: string;
  venueName: string;
  courtName: string;
};

function unwrapData<T>(payload: unknown): T {
  if (payload && typeof payload === "object" && "success" in payload && "data" in payload) {
    return (payload as { data: T }).data;
  }
  return payload as T;
}

function toIso(date: string, hhmm: string) {
  return `${date}T${hhmm}:00+07:00`;
}

function add30Minutes(hhmm: string) {
  const [h, m] = hhmm.split(":").map(Number);
  const total = h * 60 + m + 30;
  const hh = String(Math.floor(total / 60)).padStart(2, "0");
  const mm = String(total % 60).padStart(2, "0");
  return `${hh}:${mm}`;
}

function buildSlotCandidates() {
  const slots: string[] = [];
  for (let h = 5; h <= 22; h += 1) {
    slots.push(`${String(h).padStart(2, "0")}:00`);
    slots.push(`${String(h).padStart(2, "0")}:30`);
  }
  return slots;
}

function chooseDateForRule(dayType: PricingRuleDto["dayType"]) {
  const base = new Date();
  base.setHours(0, 0, 0, 0);
  base.setDate(base.getDate() + 1);

  const isWeekend = (value: Date) => {
    const day = value.getDay();
    return day === 0 || day === 6;
  };

  for (let i = 0; i < 14; i += 1) {
    const candidate = new Date(base);
    candidate.setDate(base.getDate() + i);
    if (dayType === "WEEKDAY" && isWeekend(candidate)) {
      continue;
    }
    if (dayType === "WEEKEND" && !isWeekend(candidate)) {
      continue;
    }
    const y = candidate.getFullYear();
    const m = String(candidate.getMonth() + 1).padStart(2, "0");
    const d = String(candidate.getDate()).padStart(2, "0");
    return `${y}-${m}-${d}`;
  }

  const y = base.getFullYear();
  const m = String(base.getMonth() + 1).padStart(2, "0");
  const d = String(base.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function normalizeTime(value: string | undefined) {
  if (!value) {
    return "05:00";
  }
  return value.slice(0, 5);
}

async function isSlotQuoted(courtId: string, date: string, time: string, accessToken: string) {
  const start = encodeURIComponent(toIso(date, time));
  const end = encodeURIComponent(toIso(date, add30Minutes(time)));
  const resp = await fetch(
    `${API_BASE}/api/core/pricing/quote?courtId=${courtId}&start=${start}&end=${end}&customerTier=STANDARD`,
    { headers: { Authorization: `Bearer ${accessToken}` } },
  );
  return resp.ok;
}

async function isSlotAvailable(courtId: string, date: string, time: string) {
  const start = encodeURIComponent(toIso(date, time));
  const end = encodeURIComponent(toIso(date, add30Minutes(time)));
  const resp = await fetch(`${API_BASE}/api/core/availability?courtId=${courtId}&start=${start}&end=${end}`);
  if (!resp.ok) {
    return false;
  }
  const body = unwrapData<{ available?: boolean }>(await resp.json());
  return Boolean(body?.available);
}

async function findValidSlot(courtId: string, date: string, accessToken: string, preferredTime?: string) {
  const candidates = buildSlotCandidates();
  if (preferredTime && candidates.includes(preferredTime)) {
    candidates.unshift(preferredTime);
  }
  const uniqueCandidates = Array.from(new Set(candidates));

  for (const time of uniqueCandidates) {
    const [quoted, available] = await Promise.all([
      isSlotQuoted(courtId, date, time, accessToken),
      isSlotAvailable(courtId, date, time),
    ]);
    if (quoted && available) {
      return time;
    }
  }

  return null;
}

async function loadDiscoverSeed(accessToken: string): Promise<DiscoverSeed> {
  const authHeaders = { Authorization: `Bearer ${accessToken}` };
  const venueResp = await fetch(`${API_BASE}/api/core/venues`);
  if (!venueResp.ok) {
    return {
      keyword: "E2E",
      sport: "PICKLEBALL",
      date: chooseDateForRule("WEEKDAY"),
      time: "05:00",
      venueId: "",
      courtId: "",
      venueName: "",
      courtName: "",
    };
  }

  const venues = unwrapData<VenueDto[]>(await venueResp.json());
  const courtRows = await Promise.all(
    venues.map(async (venue) => {
      const courtResp = await fetch(`${API_BASE}/api/core/courts?venueId=${venue.id}`);
      if (!courtResp.ok) {
        return [] as CourtDto[];
      }
      const courts = unwrapData<CourtDto[]>(await courtResp.json());
      return courts.map((court) => ({ ...court, venueId: venue.id }));
    }),
  );
  const courts = courtRows.flat();

  const pricingResp = await fetch(`${API_BASE}/api/core/pricing-rules`, { headers: authHeaders });
  const pricingRules = pricingResp.ok ? unwrapData<PricingRuleDto[]>(await pricingResp.json()) : [];

  const existingCourtIds = new Set(courts.map((court) => court.id));
  const candidateRule = pricingRules.find((rule) => {
    if (!rule.active || !rule.courtId || !existingCourtIds.has(rule.courtId)) {
      return false;
    }
    const tier = (rule.customerTier || "ALL").toUpperCase();
    return tier === "ALL" || tier === "STANDARD";
  });

  let candidateCourt = candidateRule ? courts.find((court) => court.id === candidateRule.courtId) : undefined;
  let candidateDate = chooseDateForRule(candidateRule?.dayType || "WEEKDAY");
  let candidateTime = normalizeTime(candidateRule?.startTime);

  if (!candidateCourt) {
    candidateCourt = courts[0];
  }

  if (!candidateCourt) {
    return {
      keyword: "E2E",
      sport: "PICKLEBALL",
      date: chooseDateForRule("WEEKDAY"),
      time: "05:00",
      venueId: "",
      courtId: "",
      venueName: "",
      courtName: "",
    };
  }

  const dayCandidates = [candidateDate, chooseDateForRule("WEEKDAY"), chooseDateForRule("WEEKEND")];
  let validTime: string | null = null;
  for (const day of Array.from(new Set(dayCandidates))) {
    validTime = await findValidSlot(candidateCourt.id, day, accessToken, candidateTime);
    if (validTime) {
      candidateDate = day;
      candidateTime = validTime;
      break;
    }
  }

  const venue = venues.find((item) => item.id === candidateCourt?.venueId) || venues[0];
  const sport = (candidateCourt.sportType?.trim() || "PICKLEBALL") as "PICKLEBALL" | "BADMINTON" | "TENNIS";

  return {
    keyword: venue?.name?.trim() || candidateCourt.name?.trim() || "E2E",
    sport,
    date: candidateDate,
    time: candidateTime,
    venueId: venue?.id || "",
    courtId: candidateCourt.id,
    venueName: venue?.name?.trim() || "",
    courtName: candidateCourt.name?.trim() || "",
  };
}

async function registerCustomerAccount() {
  const email = `smoke_${Date.now()}@example.com`;
  const response = await fetch(`${API_BASE}/api/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      email,
      displayName: "Smoke User",
      password: "Password@123",
    }),
  });

  if (!response.ok) {
    throw new Error(`Register failed with status ${response.status}`);
  }

  const payload = (await response.json()) as {
    success?: boolean;
    data?: Record<string, unknown>;
    accessToken?: string;
  };

  if (payload.success && payload.data) {
    return payload.data;
  }
  if (typeof payload.accessToken === "string") {
    return payload;
  }
  throw new Error("Register returned invalid response payload");
}

test("phase12 funnel smoke: landing -> discover -> grid -> checkout -> payment", async ({ page, request }) => {
  test.setTimeout(240_000);

  const health = await request.get(`${API_BASE}/actuator/health`);
  expect(health.ok()).toBeTruthy();

  const tokens = await registerCustomerAccount();
  const accessToken = (tokens as { accessToken?: string }).accessToken;
  if (!accessToken) {
    throw new Error("Missing access token from register API");
  }
  const seed = await loadDiscoverSeed(accessToken);

  await page.addInitScript(
    ([key, value]) => {
      window.localStorage.setItem(key, value);
    },
    [AUTH_STORAGE_KEY, JSON.stringify(tokens)] as const,
  );

  await page.goto("/");
  await page.locator('input[name="q"]').fill(seed.keyword);
  await page.locator('select[name="sport"]').selectOption(seed.sport);
  await page.locator('input[name="date"]').fill(seed.date);
  await page.locator('input[name="time"]').fill(seed.time);
  await page.locator(".landing-search-card button[type='submit']").click();

  await expect(page).toHaveURL(/\/discover/);

  const discoverError = page.locator(".inline-error").first();
  if (await discoverError.isVisible()) {
    const message = (await discoverError.textContent()) || "Discover API failed";
    throw new Error(`Discover page has inline error: ${message}`);
  }

  const targetCard = seed.courtId
    ? page.locator(`.discover-card[data-court-id="${seed.courtId}"]`).first()
    : page.locator(".discover-card").first();
  const bookButton = targetCard.locator(".booking-cta");
  await expect(bookButton).toBeVisible({ timeout: 30_000 });
  await bookButton.click();

  await expect(page).toHaveURL(/\/venues\//);
  const advancedGridButton = page.getByRole("button", { name: /Chuyển sang bảng đặt nâng cao/i });
  await expect(advancedGridButton).toBeVisible({ timeout: 30_000 });
  await advancedGridButton.click();
  await expect(page).toHaveURL(/\/booking\/grid/);
  await page.locator('.booking-grid-toolbar input[type="date"]').fill(seed.date);

  let reachedPayment = false;
  for (let attempt = 0; attempt < 5; attempt += 1) {
    const activeRow = page.locator(".timeline-row.is-active").first();
    const preferred = seed.courtName
      ? page.locator(`button.time-cell-grid[aria-label="${seed.courtName} ${seed.time}"]`)
      : page.locator("button.time-cell-grid").first();

    let selectableCell = preferred.first();
    const preferredCount = await preferred.count();
    if (preferredCount === 0 || !(await selectableCell.isEnabled())) {
      const freeCells = activeRow.locator("button.time-cell-grid:not([disabled])");
      const freeCount = await freeCells.count();
      if (freeCount === 0) {
        throw new Error("Booking grid has no selectable slot.");
      }
      const targetIndex = Math.min(attempt * 3, freeCount - 1);
      selectableCell = freeCells.nth(targetIndex);
    }

    await expect(selectableCell).toBeVisible();
    await selectableCell.click();
    await selectableCell.click();

    await page.locator(".booking-grid-summary-actions .ui-button--primary").click();
    await expect(page).toHaveURL(/\/booking\/form/);

    const formError = page.locator(".inline-error").first();
    const statusBooked = page.getByText(/Đã được đặt/i).first();
    if ((await formError.isVisible()) || (await statusBooked.isVisible())) {
      await page.locator(".simple-topbar .back-link").click();
      await expect(page).toHaveURL(/\/booking\/grid/);
      continue;
    }

    await page.locator(".checkout-footer .primary-bottom-btn").click();
    try {
      await expect(page).toHaveURL(/\/payment\//, { timeout: 20_000 });
      reachedPayment = true;
      break;
    } catch {
      await page.locator(".simple-topbar .back-link").click();
      await expect(page).toHaveURL(/\/booking\/grid/);
    }
  }

  expect(reachedPayment).toBeTruthy();

  await page.locator(".payment-right .booking-cta").click();
  await page.locator(".payment-right .ghost-cta").first().click();

  await expect(page.locator(".inline-success").first()).toBeVisible({ timeout: 30_000 });

  await expect
    .poll(async () => page.evaluate(() => (window.dataLayer ?? []).map((entry) => entry.event as string)), {
      timeout: 30_000,
    })
    .toEqual(
      expect.arrayContaining([
        "funnel_landing_search_submit",
        "funnel_discover_book_click",
        "funnel_grid_slot_range_selected",
        "funnel_grid_continue_checkout",
        "funnel_checkout_view",
        "funnel_checkout_draft_created",
        "funnel_payment_view",
        "funnel_payment_initiated",
        "funnel_payment_callback_simulated_success",
        "funnel_payment_success",
      ]),
    );
});
