import { useEffect, useMemo, useState } from "react";
import { toErrorPresentation } from "../../lib/errorPresentation";
import {
  getAdminUserById,
  listAdminUsers,
  revokeUserTokens,
  updateUserRoles,
  updateUserStatus,
  type AdminUserResponse,
  type AdminUserRole,
  type AdminUserStatus,
} from "./adminApi";

const roleOptions: AdminUserRole[] = [
  "ROLE_CUSTOMER",
  "ROLE_OWNER",
  "ROLE_STAFF",
  "ROLE_ADMIN",
  "ROLE_SUPPORT",
];

const statusOptions: AdminUserStatus[] = ["ACTIVE", "INACTIVE", "LOCKED"];

export function AdminUserManagementPage() {
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<AdminUserStatus | "ALL">("ALL");
  const [roleFilter, setRoleFilter] = useState<AdminUserRole | "ALL">("ALL");
  const [pageIndex, setPageIndex] = useState(0);
  const [rows, setRows] = useState<AdminUserResponse[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [hasPrevious, setHasPrevious] = useState(false);

  const [userId, setUserId] = useState("");
  const [selectedRoles, setSelectedRoles] = useState<AdminUserRole[]>(["ROLE_CUSTOMER"]);
  const [status, setStatus] = useState<AdminUserStatus>("ACTIVE");
  const [selectedUser, setSelectedUser] = useState<AdminUserResponse | null>(null);
  const [revokeCount, setRevokeCount] = useState<number | null>(null);

  const [busy, setBusy] = useState(false);
  const [loadingList, setLoadingList] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [traceId, setTraceId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const disabled = useMemo(() => busy || !userId.trim(), [busy, userId]);

  useEffect(() => {
    async function loadUsers() {
      try {
        setLoadingList(true);
        setError(null);
        setTraceId(null);
        const page = await listAdminUsers({
          q: query.trim() || undefined,
          status: statusFilter === "ALL" ? undefined : statusFilter,
          role: roleFilter === "ALL" ? undefined : roleFilter,
          page: pageIndex,
          size: 20,
        });
        setRows(page.content ?? []);
        setTotalElements(page.totalElements ?? 0);
        setHasPrevious(!page.first);
        setHasNext(!page.last);
      } catch (err) {
        const uiError = toErrorPresentation(err, "Không tải được danh sách user");
        setError(uiError.message);
        setTraceId(uiError.traceId ?? null);
      } finally {
        setLoadingList(false);
      }
    }
    void loadUsers();
  }, [pageIndex, query, roleFilter, statusFilter]);

  function toggleRole(role: AdminUserRole) {
    setSelectedRoles((prev) => (
      prev.includes(role) ? prev.filter((item) => item !== role) : [...prev, role]
    ));
  }

  function hydrateEditor(user: AdminUserResponse) {
    setSelectedUser(user);
    setUserId(user.userId);
    setStatus(user.status);
    setSelectedRoles(user.roles.filter((role): role is AdminUserRole => roleOptions.includes(role as AdminUserRole)));
    setRevokeCount(null);
  }

  async function handleFetchUserById() {
    if (!userId.trim()) {
      setError("Cần nhập userId.");
      setTraceId(null);
      return;
    }
    try {
      setBusy(true);
      setError(null);
      setTraceId(null);
      setNotice(null);
      const user = await getAdminUserById(userId.trim());
      hydrateEditor(user);
      setNotice("Đã tải thông tin user.");
    } catch (err) {
      const uiError = toErrorPresentation(err, "Không tải được user");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  async function handleUpdateRoles() {
    if (!userId.trim()) {
      setError("Cần nhập userId.");
      setTraceId(null);
      return;
    }
    if (!selectedRoles.length) {
      setError("Cần chọn ít nhất 1 role.");
      setTraceId(null);
      return;
    }
    try {
      setBusy(true);
      setError(null);
      setTraceId(null);
      setNotice(null);
      const result = await updateUserRoles(userId.trim(), selectedRoles);
      hydrateEditor(result);
      setNotice("Đã cập nhật roles.");
    } catch (err) {
      const uiError = toErrorPresentation(err, "Cập nhật roles thất bại");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  async function handleUpdateStatus() {
    if (!userId.trim()) {
      setError("Cần nhập userId.");
      setTraceId(null);
      return;
    }
    try {
      setBusy(true);
      setError(null);
      setTraceId(null);
      setNotice(null);
      const result = await updateUserStatus(userId.trim(), status);
      hydrateEditor(result);
      setNotice("Đã cập nhật trạng thái user.");
    } catch (err) {
      const uiError = toErrorPresentation(err, "Cập nhật trạng thái thất bại");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  async function handleRevokeTokens() {
    if (!userId.trim()) {
      setError("Cần nhập userId.");
      setTraceId(null);
      return;
    }
    try {
      setBusy(true);
      setError(null);
      setTraceId(null);
      setNotice(null);
      const result = await revokeUserTokens(userId.trim());
      setRevokeCount(result.revokedCount);
      setNotice(`Đã revoke ${result.revokedCount} refresh token.`);
    } catch (err) {
      const uiError = toErrorPresentation(err, "Revoke token thất bại");
      setError(uiError.message);
      setTraceId(uiError.traceId ?? null);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="page">
      <section className="section-header">
        <p className="eyebrow">Admin</p>
        <h1>Quản lý người dùng</h1>
        <p className="muted">Sử dụng trực tiếp endpoint `/api/auth/admin/users` để lọc và cập nhật quyền/trạng thái.</p>
      </section>

      <section className="card">
        <div className="ops-toolbar">
          <label>
            Tìm kiếm
            <input
              value={query}
              onChange={(event) => {
                setQuery(event.target.value);
                setPageIndex(0);
              }}
              placeholder="email hoặc display name"
            />
          </label>
          <label>
            Status
            <select
              value={statusFilter}
              onChange={(event) => {
                setStatusFilter(event.target.value as AdminUserStatus | "ALL");
                setPageIndex(0);
              }}
            >
              <option value="ALL">ALL</option>
              {statusOptions.map((option) => (
                <option key={option} value={option}>{option}</option>
              ))}
            </select>
          </label>
          <label>
            Role
            <select
              value={roleFilter}
              onChange={(event) => {
                setRoleFilter(event.target.value as AdminUserRole | "ALL");
                setPageIndex(0);
              }}
            >
              <option value="ALL">ALL</option>
              {roleOptions.map((role) => (
                <option key={role} value={role}>{role}</option>
              ))}
            </select>
          </label>        </div>

        <div className="ops-table-wrap">
          <table className="ops-table">
            <thead>
              <tr>
                <th>User ID</th>
                <th>Email</th>
                <th>Tên hiển thị</th>
                <th>Status</th>
                <th>Roles</th>
                <th>Chọn</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.userId}>
                  <td>#{row.userId.slice(0, 8)}</td>
                  <td>{row.email}</td>
                  <td>{row.displayName}</td>
                  <td>{row.status}</td>
                  <td>{row.roles.join(", ")}</td>
                  <td>
                    <button className="btn ghost" type="button" onClick={() => hydrateEditor(row)}>
                      Nạp
                    </button>
                  </td>
                </tr>
              ))}
              {!rows.length && !loadingList && (
                <tr>
                  <td colSpan={6} className="muted">Không có dữ liệu.</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="pagination-row">
          <button className="ghost-cta" type="button" onClick={() => setPageIndex((prev) => Math.max(0, prev - 1))} disabled={!hasPrevious || loadingList}>
            Trang trước
          </button>
          <span className="muted">Trang {pageIndex + 1} · Tổng {totalElements}</span>
          <button className="ghost-cta" type="button" onClick={() => setPageIndex((prev) => prev + 1)} disabled={!hasNext || loadingList}>
            Trang sau
          </button>
        </div>
      </section>

      <section className="grid two">
        <article className="card">
          <h3>Chi tiết / cập nhật</h3>
          <label>
            User ID
            <input value={userId} onChange={(event) => setUserId(event.target.value)} placeholder="UUID user" />
          </label>
          <button className="btn ghost" type="button" onClick={() => { void handleFetchUserById(); }} disabled={disabled}>
            Tải user theo ID
          </button>

          <div className="grid two">
            {roleOptions.map((role) => (
              <label key={role}>
                <input
                  type="checkbox"
                  checked={selectedRoles.includes(role)}
                  onChange={() => toggleRole(role)}
                />
                {role}
              </label>
            ))}
          </div>

          <label>
            Status
            <select value={status} onChange={(event) => setStatus(event.target.value as AdminUserStatus)}>
              {statusOptions.map((option) => (
                <option key={option} value={option}>{option}</option>
              ))}
            </select>
          </label>

          <div className="ops-toolbar">
            <button className="btn" type="button" onClick={() => { void handleUpdateRoles(); }} disabled={disabled}>
              Cập nhật roles
            </button>
            <button className="btn" type="button" onClick={() => { void handleUpdateStatus(); }} disabled={disabled}>
              Cập nhật status
            </button>
            <button className="btn ghost" type="button" onClick={() => { void handleRevokeTokens(); }} disabled={disabled}>
              Revoke tokens
            </button>
          </div>
        </article>

        <article className="card">
          <h3>Kết quả đang chọn</h3>
          {selectedUser ? (
            <ul className="list-clean">
              <li><strong>User:</strong> {selectedUser.userId}</li>
              <li><strong>Email:</strong> {selectedUser.email}</li>
              <li><strong>Tên:</strong> {selectedUser.displayName}</li>
              <li><strong>Status:</strong> {selectedUser.status}</li>
              <li><strong>Roles:</strong> {selectedUser.roles.join(", ")}</li>
            </ul>
          ) : (
            <p className="muted">Chưa chọn user.</p>
          )}
          {typeof revokeCount === "number" && <p className="inline-success">Số token đã revoke: {revokeCount}</p>}
        </article>
      </section>

      {error && <p className="inline-error">{error}{traceId ? ` (traceId: ${traceId})` : ""}</p>}
      {notice && <p className="inline-success">{notice}</p>}
    </main>
  );
}

