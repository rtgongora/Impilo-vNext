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

import { useEffect } from "react";
import * as Linking from "expo-linking";
import { appStore } from "../stores/appStore";
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
}

/** Provider tabs a `/provider/<segment>` sub-route may target directly. */
const PROVIDER_SUBTABS: Record<string, ProviderTabKey> = {
  dashboard: "dashboard",
  patients: "patients",
  queue: "queue",
  worklist: "queue",
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
const PROVIDER_ROUTES: Array<{ prefix: string; resolve: (subPath: string) => ProviderDeepLinkIntent }> = [
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
  const { path } = normalizeDeepLink(url);
  // The OAuth callback is owned by LoginScreen — never intercept it here.
  if (path.includes("auth/callback")) return null;
  const match = PROVIDER_ROUTES.find((route) => path === route.prefix || path.startsWith(`${route.prefix}/`));
  if (!match) return null;
  const subPath = path.slice(match.prefix.length);
  return match.resolve(subPath);
}

export function applyProviderDeepLink(intent: ProviderDeepLinkIntent): void {
  const store = appStore.getState();
  store.setMode(intent.mode);
  store.setProviderTab(intent.tab);
}

/** Route a URL if it is a recognized deep link. Returns true if handled. */
export function handleProviderDeepLink(url: string | null | undefined): boolean {
  if (!url) return false;
  const intent = resolveProviderDeepLink(url);
  if (!intent) return false;
  applyProviderDeepLink(intent);
  return true;
}

/** Wire the cold-start URL plus warm foreground events. Mount once near root. */
export function useDeepLinkRouting(): void {
  useEffect(() => {
    Linking.getInitialURL()
      .then((url) => handleProviderDeepLink(url))
      .catch(() => {
        /* no initial URL — normal launch */
      });
    const subscription = Linking.addEventListener("url", ({ url }) => {
      handleProviderDeepLink(url);
    });
    return () => subscription.remove();
  }, []);
}
