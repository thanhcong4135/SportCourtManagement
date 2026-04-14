import { apiFetch, createIdempotencyKey } from "../../lib/api";

export type AdminUserRole =
  | "ROLE_CUSTOMER"
  | "ROLE_OWNER"
  | "ROLE_STAFF"
  | "ROLE_ADMIN"
  | "ROLE_SUPPORT"
  | "ROLE_SERVICE_PAYMENT"
  | "ROLE_SERVICE_NOTIFICATION";

export type AdminUserStatus = "ACTIVE" | "INACTIVE" | "LOCKED";

export type AdminUserResponse = {
  userId: string;
  email: string;
  displayName: string;
  status: AdminUserStatus;
  roles: string[];
};

export type SpringPage<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
};

export type TokenRevokeResponse = {
  userId: string;
  revokedCount: number;
};

export async function listAdminUsers(params: {
  q?: string;
  status?: AdminUserStatus;
  role?: AdminUserRole;
  page?: number;
  size?: number;
}) {
  const search = new URLSearchParams();
  if (params.q) {
    search.set("q", params.q);
  }
  if (params.status) {
    search.set("status", params.status);
  }
  if (params.role) {
    search.set("role", params.role);
  }
  search.set("page", String(params.page ?? 0));
  search.set("size", String(params.size ?? 20));
  search.set("sort", "createdAt,desc");
  return apiFetch<SpringPage<AdminUserResponse>>(`/api/auth/admin/users?${search.toString()}`);
}

export async function getAdminUserById(userId: string) {
  return apiFetch<AdminUserResponse>(`/api/auth/admin/users/${userId}`);
}

export async function updateUserRoles(userId: string, roles: AdminUserRole[]) {
  return apiFetch<AdminUserResponse>(`/api/auth/admin/users/${userId}/roles`, {
    method: "PUT",
    headers: {
      "Idempotency-Key": createIdempotencyKey("admin-user-roles"),
    },
    body: JSON.stringify({ roles }),
  });
}

export async function updateUserStatus(userId: string, status: AdminUserStatus) {
  return apiFetch<AdminUserResponse>(`/api/auth/admin/users/${userId}/status`, {
    method: "PUT",
    headers: {
      "Idempotency-Key": createIdempotencyKey("admin-user-status"),
    },
    body: JSON.stringify({ status }),
  });
}

export async function revokeUserTokens(userId: string) {
  return apiFetch<TokenRevokeResponse>(`/api/auth/admin/users/${userId}/revoke-tokens`, {
    method: "POST",
    headers: {
      "Idempotency-Key": createIdempotencyKey("admin-user-revoke"),
    },
  });
}
