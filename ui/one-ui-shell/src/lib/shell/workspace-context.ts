/**
 * Workspace-context route classification — decides how much chrome a route
 * deserves. Landing/hub surfaces are "navigating" contexts (full nav rail,
 * hero headers); opened applications and records are "focused work" contexts
 * (icon-only rail, compact headers, maximum practical workspace).
 *
 * Pure functions — unit-testable, no React.
 */

export type NavRailMode = "expanded" | "compact" | "hidden";

/**
 * Surfaces where the user is primarily navigating or living their health —
 * the full icon-and-label navigation earns its width here.
 */
const NAVIGATION_PREFIXES = [
  "/home",
  "/welcome",
  "/discover",
  "/social",
  "/communities",
  "/pages",
  "/citizen",
  "/professional",
  "/guidance",
  "/search",
  "/facility",
  "/workspace",
  "/shift",
];

/**
 * Hub landing pages of applications/zones: expanded at the front door,
 * compact once the user steps inside to work.
 */
const HUB_LANDINGS = [
  "/",
  "/clinical",
  "/wellness",
  "/learning",
  "/marketplace",
  "/enterprise",
  "/finance",
  "/admin",
  "/operations",
  "/registry",
  "/registry-admin",
  "/organization-admin",
  "/public-health",
  "/support",
  "/pharmacy",
  "/inventory",
  "/queue",
  "/lab",
  "/madi",
  "/live",
  "/telemedicine",
  "/reports",
  "/settings",
  "/erp",
  "/beds",
  "/scheduling",
  "/caregiving",
  "/monitoring",
  "/wallet",
  "/developer",
  "/ask",
  "/intelligence",
];

function normalize(pathname: string): string {
  if (!pathname) return "/";
  const noQuery = pathname.split(/[?#]/)[0] ?? "/";
  if (noQuery.length > 1 && noQuery.endsWith("/")) return noQuery.slice(0, -1);
  return noQuery || "/";
}

/**
 * True when the route is an opened application / operational workspace /
 * record — focused work that deserves the maximum practical workspace.
 */
export function isFocusedWorkspaceRoute(pathname: string): boolean {
  const path = normalize(pathname);
  if (NAVIGATION_PREFIXES.some((p) => path === p || path.startsWith(`${p}/`))) return false;
  if (HUB_LANDINGS.includes(path)) return false;
  return true;
}

/**
 * Resolve the nav rail mode from route context, the user's persisted
 * preference, and focus mode. Manual preference wins over route context;
 * focus mode (safety/immersion) wins over everything.
 */
export function resolveNavRailMode(args: {
  pathname: string;
  pref: "auto" | "expanded" | "compact";
  focusMode: boolean;
}): NavRailMode {
  if (args.focusMode) return "hidden";
  if (args.pref === "expanded" || args.pref === "compact") return args.pref;
  return isFocusedWorkspaceRoute(args.pathname) ? "compact" : "expanded";
}
