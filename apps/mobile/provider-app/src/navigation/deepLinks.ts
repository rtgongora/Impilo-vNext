/**
 * Deep-link routing — provider app.
 *
 * Like the citizen app, the provider app navigates through the Zustand
 * `appStore` (mode + provider tab), not a React Navigation container, so this
 * module is the `linking` equivalent: accepted prefixes, a web-route → app
 * destination map, and (via `useDeepLinkRouting`) an inbound-URL listener.
 *
 * Link classes handled by the same code:
 *   - custom scheme   impilo-provider://provider/queue
 *   - universal link  https://impilo.mohcc.gov.zw/provider/queue
 *
 * Universal-link verification requires the website's .well-known files to be
 * signed with the real Apple Team ID / Android release cert — see
 * apps/mobile/DEEP_LINKING.md. Custom-scheme links work today regardless.
 *
 * No sensitive data is carried: provider deep links only select a mode/tab; any
 * clinical context is loaded behind auth once the surface opens.
 */

import { useEffect, useRef } from "react";
import * as Linking from "expo-linking";
import { appStore } from "../stores/appStore";
import { useSwitchAppMode } from "../hooks/useSwitchAppMode";
import type { AppMode, ProviderTabKey } from "../types";

export const WEB_ORIGIN = "https://impilo.mohcc.gov.zw";
export const APP_SCHEME = "impilo-provider";

/** Prefixes React-Navigation-style `linking` config would list. */
export const DEEP_LINK_PREFIXES = [
  Linking.createURL("/"), // exp:// (Expo Go) + impilo-provider://
  `${APP_SCHEME}://`,
  `${WEB_ORIGIN}/`,
];

export interface ProviderDeepLinkIntent {
  mode: AppMode;
  tab: ProviderTabKey;
  /** Optional Professional-tab surface (e.g. regulatory, contribute). */
  professionalSurface?: string;
  /** Contributor invite id for student-registration redeem. */
  inviteId?: string;
}

/** Provider tabs a `/provider/<segment>` sub-route may target directly. */
const PROVIDER_SUBTABS: Record<string, ProviderTabKey> = {
  dashboard: "dashboard",
  patients: "patients",
  queue: "queue",
  worklist: "queue",
  theatre: "theatre",
  results: "results",
  messaging: "messaging",
  tools: "tools",
  apps: "apps",
  professional: "professional",
  diagnostics: "diagnostics",
};

/**
 * Canonical web route → provider destination.
 *
 * | Web path (impilo.mohcc.gov.zw) | In-app destination                     |
 * | ------------------------------ | -------------------------------------- |
 * | /provider                      | Provider mode → Dashboard              |
 * | /provider/<tab>                | Provider mode → matching tab (if any)  |
 * | /work                          | Provider mode → Worklist (queue)       |
 *
 * Longest-prefix wins; `/provider` matches both `/provider` and `/provider/*`.
 */
const PROVIDER_ROUTES: Array<{
  prefix: string;
  resolve: (subPath: string, params: Record<string, string>) => ProviderDeepLinkIntent;
}> = [
  {
    prefix: "/professional/regulatory/contribute",
    resolve: (subPath, params) => ({
      mode: "provider",
      tab: "professional",
      professionalSurface: "regulatory",
      inviteId: subPath.split("/").filter(Boolean)[0] ?? params.inviteId,
    }),
  },
  {
    prefix: "/professional/regulatory",
    resolve: () => ({
      mode: "provider",
      tab: "professional",
      professionalSurface: "regulatory",
    }),
  },
  {
    prefix: "/provider",
    resolve: (subPath) => {
      const firstSegment = subPath.split("/").filter(Boolean)[0];
      return { mode: "provider", tab: (firstSegment && PROVIDER_SUBTABS[firstSegment]) || "dashboard" };
    },
  },
  {
    prefix: "/work",
    resolve: () => ({ mode: "provider", tab: "queue" }),
  },
];

/**
 * Normalize a custom-scheme OR https URL to a leading-slash web path + params.
 * See the citizen-app twin for the hostname-handling rationale.
 */
export function normalizeDeepLink(url: string): { path: string; params: Record<string, string> } {
  const parsed = Linking.parse(url);
  const isWeb = /^https?:\/\//i.test(url);
  const segments: string[] = [];
  if (!isWeb && parsed.hostname) segments.push(parsed.hostname);
  if (parsed.path) segments.push(parsed.path);
  const path = `/${segments.join("/").replace(/^\/+/, "").replace(/\/+$/, "")}`;
  const params: Record<string, string> = {};
  for (const [key, value] of Object.entries(parsed.queryParams ?? {})) {
    if (typeof value === "string") params[key] = value;
    else if (Array.isArray(value) && typeof value[0] === "string") params[key] = value[0];
  }
  return { path, params };
}

/** Pure resolver: URL → destination, or null if not a routable link. */
export function resolveProviderDeepLink(url: string): ProviderDeepLinkIntent | null {
  const { path, params } = normalizeDeepLink(url);
  // The OAuth callback is owned by LoginScreen — never intercept it here.
  if (path.includes("auth/callback")) return null;
  const match = PROVIDER_ROUTES.find((route) => path === route.prefix || path.startsWith(`${route.prefix}/`));
  if (!match) return null;
  const subPath = path.slice(match.prefix.length);
  return match.resolve(subPath, params);
}

/**
 * Applies a resolved deep-link intent.
 *
 * `enterMode` must be `useSwitchAppMode`'s switcher: every route here resolves
 * to `provider`, a governed mode, so following an inbound link has to mint a
 * clinical duty token rather than silently dropping the person into the
 * clinical workspace under whatever token they happened to be holding. The tab
 * is applied regardless — it is pure navigation and carries no authority — so a
 * link still lands somewhere sensible if the mint is refused.
 */
export function applyProviderDeepLink(
  intent: ProviderDeepLinkIntent,
  enterMode: (mode: AppMode) => void
): void {
  const store = appStore.getState();
  store.setProviderTab(intent.tab);
  if (intent.professionalSurface) {
    store.setProfessionalSurfaceRequest(intent.professionalSurface);
  }
  if (intent.inviteId) {
    store.setContributeInviteRequest(intent.inviteId);
  }
  enterMode(intent.mode);
}

/** Route a URL if it is a recognized deep link. Returns true if handled. */
export function handleProviderDeepLink(
  url: string | null | undefined,
  enterMode: (mode: AppMode) => void
): boolean {
  if (!url) return false;
  const intent = resolveProviderDeepLink(url);
  if (!intent) return false;
  applyProviderDeepLink(intent, enterMode);
  return true;
}

/** Wire the cold-start URL plus warm foreground events. Mount once near root. */
export function useDeepLinkRouting(): void {
  const { switchAppMode } = useSwitchAppMode();
  // Held in a ref so the listener effect stays mount-once: switchAppMode is
  // rebuilt whenever resolved contexts or the active context change, and
  // re-subscribing to Linking on every such change would drop the cold-start
  // URL handling and double-register the warm listener.
  const enterModeRef = useRef<(mode: AppMode) => void>(() => {});
  enterModeRef.current = (mode: AppMode) => void switchAppMode(mode);

  useEffect(() => {
    const enterMode = (mode: AppMode) => enterModeRef.current(mode);
    Linking.getInitialURL()
      .then((url) => handleProviderDeepLink(url, enterMode))
      .catch(() => {
        /* no initial URL — normal launch */
      });
    const subscription = Linking.addEventListener("url", ({ url }) => {
      handleProviderDeepLink(url, enterMode);
    });
    return () => subscription.remove();
  }, []);
}
