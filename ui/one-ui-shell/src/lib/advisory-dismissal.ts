/**
 * Anonymous Service-Advisory dismissal store.
 *
 * Mirrors the localStorage + version-dismissal approach of {@link useConsentStore}:
 * a citizen can dismiss a Nompilo service advisory WITHOUT an account, and the
 * dismissal persists across page loads on that device only. No identity, no server
 * account and no PII are involved — we key purely on the advisory's own id (the
 * "period": one persisted advisory instance). When the advisory content changes the
 * backend issues a new id, so an updated advisory re-appears exactly as a bumped
 * consent version would.
 *
 * The record is best-effort and defensive: any storage/parse failure degrades to
 * "not dismissed" so a citizen is never wrongly denied an advisory, and never crashes
 * a public page over a corrupt entry.
 */

const STORAGE_KEY = "exp:advisory_dismissed";

type DismissalMap = Record<string, string>; // advisoryId -> ISO dismissedAt

function readMap(): DismissalMap {
  if (typeof window === "undefined") return {};
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as unknown;
    if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
      return parsed as DismissalMap;
    }
    return {};
  } catch {
    return {};
  }
}

function writeMap(map: DismissalMap): void {
  if (typeof window === "undefined") return;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(map));
  } catch {
    /* storage unavailable (private mode / quota) — dismissal is best-effort only */
  }
}

/** True when this advisory id has already been dismissed on this device. */
export function isAdvisoryDismissed(advisoryId: string): boolean {
  if (!advisoryId) return false;
  return Boolean(readMap()[advisoryId]);
}

/** Persist a dismissal for this advisory id (idempotent). */
export function dismissAdvisory(advisoryId: string): void {
  if (!advisoryId) return;
  const map = readMap();
  if (map[advisoryId]) return;
  map[advisoryId] = new Date().toISOString();
  writeMap(map);
}

/** Return the set of advisory ids dismissed on this device. */
export function dismissedAdvisoryIds(): Set<string> {
  return new Set(Object.keys(readMap()));
}

const ANON_KEY_STORAGE = "exp:advisory_anon_key";

/**
 * A stable, opaque, per-device key sent as {@code anonDismissalKey} on impression/dismiss
 * events so the backend can de-duplicate interactions WITHOUT any account or PII. It is a
 * random token generated once per device and never leaves the advisory analytics lane.
 */
export function getAnonDismissalKey(): string {
  if (typeof window === "undefined") return "anon";
  try {
    let key = localStorage.getItem(ANON_KEY_STORAGE);
    if (!key) {
      const rand =
        typeof crypto !== "undefined" && "randomUUID" in crypto
          ? crypto.randomUUID()
          : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
      key = `anon-${rand}`;
      localStorage.setItem(ANON_KEY_STORAGE, key);
    }
    return key;
  } catch {
    return "anon";
  }
}
