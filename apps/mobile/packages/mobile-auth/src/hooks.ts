/**
 * React Hooks for Auth — Provides reactive access to auth state.
 *
 * useAuth()    — full auth state + actions
 * useSession() — session context only (tenantId, actorType, etc.)
 */

import { useSyncExternalStore, useCallback } from "react";
import { authStore } from "./authStore";
import type { AuthState } from "./authStore";
import type { SessionContext } from "@impilo/mobile-trust";

/**
 * Subscribe to the full auth store. Returns all auth state and actions.
 */
export function useAuth(): AuthState {
  return useSyncExternalStore(
    authStore.subscribe,
    () => authStore.getState(),
    () => authStore.getState()
  );
}

/**
 * Subscribe to the session context only.
 * Re-renders only when session changes.
 */
export function useSession(): {
  session: SessionContext | null;
  isAuthenticated: boolean;
} {
  const selectSession = useCallback(() => {
    const state = authStore.getState();
    return { session: state.session, isAuthenticated: state.isAuthenticated };
  }, []);

  return useSyncExternalStore(
    authStore.subscribe,
    selectSession,
    selectSession
  );
}

/**
 * Get a valid access token. Triggers refresh if near expiry.
 * Returns a function that resolves to the current valid token.
 */
export function useAccessToken(): () => Promise<string> {
  return useCallback(() => authStore.getState().getValidToken(), []);
}
