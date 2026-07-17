/**
 * Deep-link routing — citizen app.
 *
 * The citizen app drives navigation through the Zustand `appStore` (active tab
 * + one-shot section requests), not a React Navigation `NavigationContainer`.
 * There is therefore no `linking` prop to hand to a container; instead this
 * module is the equivalent: it declares the accepted URL prefixes, maps the
 * canonical web routes on impilo.mohcc.gov.zw to in-app destinations, and
 * (via `useDeepLinkRouting`) listens for inbound URLs and dispatches them.
 *
 * Two link classes are handled, both by the same code:
 *   - custom scheme   impilo-citizen://welcome/find-care
 *   - universal link  https://impilo.mohcc.gov.zw/welcome/find-care
 *
 * Universal-link *verification* (tapping the https URL opening the app without
 * a chooser) additionally requires the .well-known files on the website to be
 * signed with the real Apple Team ID / Android release cert — see
 * apps/mobile/DEEP_LINKING.md. Custom-scheme links work today regardless.
 *
 * No sensitive data is carried in deep links: only opaque context ids
 * (facility id, feedback case reference) that are re-fetched behind auth.
 */

import { useEffect } from "react";
import * as Linking from "expo-linking";
import { appStore } from "../stores/appStore";
import type { CitizenTab } from "../types";

export const WEB_ORIGIN = "https://impilo.mohcc.gov.zw";
export const APP_SCHEME = "impilo-citizen";

/** Prefixes React-Navigation-style `linking` config would list. */
export const DEEP_LINK_PREFIXES = [
  Linking.createURL("/"), // exp:// (Expo Go) + impilo-citizen://
  `${APP_SCHEME}://`,
  `${WEB_ORIGIN}/`,
];

/**
 * An in-app destination resolved from a URL. `section` is a PersonalScreen
 * section id consumed via `setPersonalSectionRequest`.
 */
export interface CitizenDeepLinkIntent {
  tab: CitizenTab;
  section?: string;
  facilityId?: string;
  facilityName?: string;
  feedbackReference?: string;
}

/**
 * Canonical web route → citizen destination.
 *
 * | Web path (impilo.mohcc.gov.zw) | In-app destination                    |
 * | ------------------------------ | ------------------------------------- |
 * | /welcome/find-care             | Health → Discover providers           |
 * | /appointments                  | Health → Appointments                 |
 * | /bills                         | Health → Finance (bills & payments)   |
 * | /welcome/report                | Health → Feedback (report a concern)  |
 * | /get-involved                  | Public health (participation)         |
 *
 * Longest-prefix wins, so more specific routes must precede shorter ones.
 */
const CITIZEN_ROUTES: Array<{ prefix: string; resolve: (params: Record<string, string>) => CitizenDeepLinkIntent }> = [
  {
    prefix: "/welcome/find-care",
    resolve: (params) => ({
      tab: "personal",
      section: "discover-providers",
      facilityId: params.facility ?? params.facilityId,
      facilityName: params.facilityName,
    }),
  },
  {
    prefix: "/welcome/report",
    resolve: (params) => ({
      tab: "personal",
      section: params.ref || params.reference ? "rito-track" : "rito-feedback",
      feedbackReference: params.ref ?? params.reference,
    }),
  },
  {
    prefix: "/appointments",
    resolve: () => ({ tab: "personal", section: "appointments" }),
  },
  {
    prefix: "/bills",
    resolve: () => ({ tab: "personal", section: "finance" }),
  },
  {
    prefix: "/get-involved",
    resolve: () => ({ tab: "public_health" }),
  },
];

/**
 * Normalize a custom-scheme OR https URL to a leading-slash web path and its
 * query params. For https links the hostname is the web domain and is dropped;
 * for custom-scheme links the hostname is really the first path segment and is
 * kept (`impilo-citizen://welcome/find-care` → `/welcome/find-care`).
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

/** Pure resolver: URL → destination, or null if it is not a routable link. */
export function resolveCitizenDeepLink(url: string): CitizenDeepLinkIntent | null {
  const { path, params } = normalizeDeepLink(url);
  // The OAuth callback is owned by LoginScreen — never intercept it here.
  if (path.includes("auth/callback")) return null;
  const match = CITIZEN_ROUTES.find((route) => path === route.prefix || path.startsWith(`${route.prefix}/`));
  return match ? match.resolve(params) : null;
}

/** Apply a resolved destination to the app store. */
export function applyCitizenDeepLink(intent: CitizenDeepLinkIntent): void {
  const store = appStore.getState();
  store.setActiveTab(intent.tab);
  if (intent.facilityId) {
    store.setSelectedFacility(intent.facilityId, intent.facilityName ?? intent.facilityId);
  }
  if (intent.feedbackReference) {
    store.setRitoTrackReference(intent.feedbackReference);
  }
  if (intent.section) {
    store.setPersonalSectionRequest(intent.section);
  }
}

/** Route a URL if it is a recognized deep link. Returns true if handled. */
export function handleCitizenDeepLink(url: string | null | undefined): boolean {
  if (!url) return false;
  const intent = resolveCitizenDeepLink(url);
  if (!intent) return false;
  applyCitizenDeepLink(intent);
  return true;
}

/**
 * Wire inbound deep links: the cold-start URL plus warm foreground events.
 * Mount once, near the navigation root.
 */
export function useDeepLinkRouting(): void {
  useEffect(() => {
    Linking.getInitialURL()
      .then((url) => handleCitizenDeepLink(url))
      .catch(() => {
        /* no initial URL — normal launch */
      });
    const subscription = Linking.addEventListener("url", ({ url }) => {
      handleCitizenDeepLink(url);
    });
    return () => subscription.remove();
  }, []);
}
