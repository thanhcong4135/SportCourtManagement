/* eslint-disable react-refresh/only-export-components */
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";
import { apiFetch, configureApiAuthBridge, type AuthTokens } from "../lib/api";

type RegisterPayload = {
  email: string;
  password: string;
  displayName: string;
};

type LoginPayload = {
  email: string;
  password: string;
};

export type AuthRole = "CUSTOMER" | "OWNER" | "ADMIN" | "STAFF" | "SUPPORT";

type AuthContextType = {
  token: AuthTokens | null;
  isAuthenticated: boolean;
  roles: string[];
  userId?: string;
  login: (payload: LoginPayload) => Promise<void>;
  register: (payload: RegisterPayload) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
  hasAnyRole: (roles: AuthRole[]) => boolean;
};

const STORAGE_KEY = "sportcourt.frontend.auth";
const AuthContext = createContext<AuthContextType | null>(null);

function readStoredToken(): AuthTokens | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as AuthTokens;
  } catch {
    localStorage.removeItem(STORAGE_KEY);
    return null;
  }
}

function writeStoredToken(token: AuthTokens | null) {
  if (!token) {
    localStorage.removeItem(STORAGE_KEY);
    return;
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(token));
}

function normalizeRoles(token: AuthTokens | null): string[] {
  if (!token?.roles) {
    return [];
  }
  return token.roles.map((role) => role.replace(/^ROLE_/, "").toUpperCase());
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setToken] = useState<AuthTokens | null>(() => readStoredToken());
  const tokenRef = useRef<AuthTokens | null>(token);

  const updateToken = useCallback((next: AuthTokens | null) => {
    tokenRef.current = next;
    setToken(next);
    writeStoredToken(next);
  }, []);

  const refreshWithToken = useCallback(async (refreshToken: string) => {
    const response = await apiFetch<AuthTokens>("/api/auth/refresh", {
      method: "POST",
      body: JSON.stringify({ refreshToken }),
    });
    updateToken(response);
    return response;
  }, [updateToken]);

  const login = useCallback(async (payload: LoginPayload) => {
    const response = await apiFetch<AuthTokens>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify(payload),
    });
    updateToken(response);
  }, [updateToken]);

  const register = useCallback(async (payload: RegisterPayload) => {
    const response = await apiFetch<AuthTokens>("/api/auth/register", {
      method: "POST",
      body: JSON.stringify(payload),
    });
    updateToken(response);
  }, [updateToken]);

  const refresh = useCallback(async () => {
    const currentToken = tokenRef.current;
    if (!currentToken?.refreshToken) {
      throw new Error("No refresh token");
    }
    await refreshWithToken(currentToken.refreshToken);
  }, [refreshWithToken]);

  const logout = useCallback(async () => {
    const currentToken = tokenRef.current;
    if (currentToken?.refreshToken) {
      try {
        await apiFetch<void>(
          "/api/auth/logout",
          {
            method: "POST",
            body: JSON.stringify({ refreshToken: currentToken.refreshToken }),
          },
          currentToken.accessToken,
        );
      } catch {
        // Intentionally ignore logout failures and clear local session.
      }
    }
    updateToken(null);
  }, [updateToken]);

  useEffect(() => {
    configureApiAuthBridge({
      getTokens: () => tokenRef.current,
      refreshTokens: async () => {
        const currentToken = tokenRef.current;
        if (!currentToken?.refreshToken) {
          return null;
        }
        try {
          return await refreshWithToken(currentToken.refreshToken);
        } catch {
          updateToken(null);
          return null;
        }
      },
      clearTokens: () => updateToken(null),
    });
  }, [refreshWithToken, updateToken]);

  const roles = useMemo(() => normalizeRoles(token), [token]);

  const hasAnyRole = useCallback((expected: AuthRole[]) => {
    if (!expected.length) {
      return true;
    }
    return expected.some((role) => roles.includes(role));
  }, [roles]);

  const value = useMemo<AuthContextType>(() => ({
    token,
    isAuthenticated: Boolean(token?.accessToken),
    roles,
    userId: token?.userId,
    login,
    register,
    logout,
    refresh,
    hasAnyRole,
  }), [token, roles, login, register, logout, refresh, hasAnyRole]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return ctx;
}
