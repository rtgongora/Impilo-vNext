import type { PurposeOfUse, SessionInfo, WorkContext } from "@/lib/contracts";

const STORAGE_KEY = "impilo_one_ui_shell_session_v1";

export interface PersistedShellSession {
  session: SessionInfo;
  workContext: WorkContext;
  purposeOfUse: PurposeOfUse;
}

function sessionWithoutRefreshToken(session: SessionInfo): SessionInfo {
  return { ...session, refreshToken: undefined };
}

export function loadPersistedShellSession(): PersistedShellSession | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as PersistedShellSession;
    if (!parsed?.session?.accessToken || typeof parsed.session.expiresAt !== "number")
      return null;
    parsed.session = sessionWithoutRefreshToken(parsed.session);
    if (parsed.session.expiresAt <= Date.now() + 5_000) {
      window.sessionStorage.removeItem(STORAGE_KEY);
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

export function savePersistedShellSession(data: PersistedShellSession): void {
  if (typeof window === "undefined") return;
  const safe: PersistedShellSession = {
    ...data,
    session: sessionWithoutRefreshToken(data.session),
  };
  window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(safe));
}

export function clearPersistedShellSession(): void {
  if (typeof window === "undefined") return;
  window.sessionStorage.removeItem(STORAGE_KEY);
}
