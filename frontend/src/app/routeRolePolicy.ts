import type { AuthRole } from "../context/AuthContext";

export const CUSTOMER_BOOKING_ROLES: AuthRole[] = ["CUSTOMER", "OWNER", "ADMIN"];

export const OPS_DASHBOARD_ROLES: AuthRole[] = ["ADMIN", "OWNER", "STAFF"];

export const OPS_NOTIFICATIONS_ROLES: AuthRole[] = ["ADMIN", "OWNER", "STAFF"];

export const OPS_PRICING_ROLES: AuthRole[] = ["ADMIN", "OWNER"];

export const OPS_USER_MANAGEMENT_ROLES: AuthRole[] = ["ADMIN"];

export const OPS_DLQ_ROLES: AuthRole[] = ["ADMIN"];

