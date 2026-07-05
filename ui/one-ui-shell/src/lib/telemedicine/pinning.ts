/**
 * Clinical routing pins — client-side fast-access list for people, groups,
 * facilities, workspaces and virtual hospitals.
 *
 * Pinning improves DISCOVERY only. It never bypasses policy: a pinned target
 * is just a deep link into the same governed routing/request flows. Pins are
 * stored per-browser in localStorage (no backend claim is made).
 */

export type PinnedTargetKind =
  | "PROVIDER"
  | "GROUP_TYPE"
  | "FACILITY"
  | "WORKSPACE"
  | "VIRTUAL_HOSPITAL";

export interface PinnedTarget {
  kind: PinnedTargetKind;
  /** Stable id in the source directory (provider id, facility id, vh id, …). */
  id: string;
  label: string;
  /** Optional secondary line (specialty, province, facility name…). */
  detail?: string;
  pinnedAt: string;
}

const STORAGE_KEY = "impilo.telemedicine.routing-pins.v1";
const MAX_PINS = 24;

function safeParse(raw: string | null): PinnedTarget[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(
      (p): p is PinnedTarget =>
        p && typeof p === "object" && typeof p.id === "string" && typeof p.kind === "string",
    );
  } catch {
    return [];
  }
}

export function loadPins(): PinnedTarget[] {
  if (typeof window === "undefined") return [];
  return safeParse(window.localStorage.getItem(STORAGE_KEY));
}

export function isPinned(pins: PinnedTarget[], kind: PinnedTargetKind, id: string): boolean {
  return pins.some((p) => p.kind === kind && p.id === id);
}

export function addPin(pins: PinnedTarget[], pin: Omit<PinnedTarget, "pinnedAt">): PinnedTarget[] {
  if (isPinned(pins, pin.kind, pin.id)) return pins;
  const next = [{ ...pin, pinnedAt: new Date().toISOString() }, ...pins].slice(0, MAX_PINS);
  persist(next);
  return next;
}

export function removePin(pins: PinnedTarget[], kind: PinnedTargetKind, id: string): PinnedTarget[] {
  const next = pins.filter((p) => !(p.kind === kind && p.id === id));
  persist(next);
  return next;
}

function persist(pins: PinnedTarget[]): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(pins));
  } catch {
    // Storage may be unavailable (private mode/quota); pins simply stay in memory.
  }
}
