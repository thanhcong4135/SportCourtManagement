type AnalyticsPayload = Record<string, unknown>;

type DataLayerEvent = AnalyticsPayload & {
  event: string;
  timestamp: string;
};

declare global {
  interface Window {
    dataLayer?: DataLayerEvent[];
  }
}

export function trackEvent(event: string, payload: AnalyticsPayload = {}) {
  const item: DataLayerEvent = {
    event,
    timestamp: new Date().toISOString(),
    ...payload,
  };

  if (typeof window !== "undefined") {
    if (!Array.isArray(window.dataLayer)) {
      window.dataLayer = [];
    }
    window.dataLayer.push(item);
    window.dispatchEvent(new CustomEvent("sportcourt:analytics", { detail: item }));
  }

  if (import.meta.env.DEV) {
    // Keep it short and searchable while building funnel tracking.
    console.debug("[analytics]", item);
  }
}

