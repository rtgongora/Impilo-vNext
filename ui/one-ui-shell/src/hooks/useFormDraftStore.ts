"use client";

/**
 * Generic Form Draft Store — versioned, best-effort localStorage snapshots of in-progress
 * form input, so an interrupted citizen returns to their entered values.
 *
 * Generalises the versioned-localStorage pattern of useFindCareJourneyStore: a bumpable
 * version discards stale shapes, writes are best-effort (private mode / full storage never
 * throw), and each draft is namespaced by a caller-supplied key. It pairs with the existing
 * returnTo / gateway-intent path — those restore the exact route/step; this restores the
 * field values the citizen had typed before a sign-in interruption or session expiry.
 *
 * DOCTRINE (never persist secrets): field names that look like credentials — passwords,
 * OTP/2FA codes, card/PIN/CVV, payment tokens, secrets — are always stripped before write,
 * on top of any caller-supplied `exclude` list. Drafts are cleared on submit/success.
 */

import { useCallback, useEffect, useState, useRef } from "react";

/** Bumped when the wrapper shape changes; a mismatch discards all stored drafts on read. */
export const FORM_DRAFT_VERSION = "2026-07-17";

const KEY_PREFIX = "exp:form_draft:";

/**
 * Lightweight registry of resumable drafts, so the shell can surface a "Continue where you
 * left off" list without knowing every form key ahead of time. It lives under its own key
 * (a new key needs no version bump of stored drafts) and is versioned with the same
 * FORM_DRAFT_VERSION so a shape change discards it cleanly. It never holds field values —
 * only a label, a route to resume at, and a timestamp — so no sensitive data leaks here.
 */
const INDEX_KEY = "exp:form_draft_index";

/**
 * Field-name fragments that must never be persisted. Matched case-insensitively as a
 * substring of the key, so `confirmPassword`, `otpCode`, `cardNumber`, `cvv` are all caught.
 */
const SENSITIVE_KEY_FRAGMENTS = [
  "password",
  "passcode",
  "otp",
  "2fa",
  "mfa",
  "pin",
  "cvv",
  "cvc",
  "card",
  "secret",
  "token",
  "ssn",
  "nationalid",
  "national_id",
] as const;

interface StoredDraft<T> {
  version: string;
  updatedAt: number;
  values: Partial<T>;
}

/** A single resumable-draft registry entry — no field values, only where to resume. */
export interface ResumableDraft {
  key: string;
  label: string;
  href: string;
  updatedAt: number;
}

/** Optional metadata a caller supplies so its draft appears in the resume index. */
export interface FormDraftMeta {
  label?: string;
  href?: string;
}

interface StoredIndex {
  version: string;
  entries: ResumableDraft[];
}

function storageKey(key: string): string {
  return `${KEY_PREFIX}${key}`;
}

// ── Resume index (internal helpers) ─────────────────────────────────────────────────

function readIndex(): ResumableDraft[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = window.localStorage.getItem(INDEX_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as StoredIndex;
    if (!parsed || parsed.version !== FORM_DRAFT_VERSION || !Array.isArray(parsed.entries)) {
      window.localStorage.removeItem(INDEX_KEY);
      return [];
    }
    return parsed.entries.filter(
      (e): e is ResumableDraft =>
        !!e &&
        typeof e.key === "string" &&
        typeof e.label === "string" &&
        typeof e.href === "string" &&
        typeof e.updatedAt === "number",
    );
  } catch {
    return [];
  }
}

function writeIndex(entries: ResumableDraft[]): void {
  if (typeof window === "undefined") return;
  try {
    if (entries.length === 0) {
      window.localStorage.removeItem(INDEX_KEY);
      return;
    }
    const payload: StoredIndex = { version: FORM_DRAFT_VERSION, entries };
    window.localStorage.setItem(INDEX_KEY, JSON.stringify(payload));
  } catch {
    /* best-effort — index is a convenience surface, never load-bearing */
  }
}

function upsertIndexEntry(entry: ResumableDraft): void {
  const entries = readIndex().filter((e) => e.key !== entry.key);
  entries.push(entry);
  writeIndex(entries);
}

/** Remove a draft's index entry (on clear, on stale-read prune, or on discard). */
function removeIndexEntry(key: string): void {
  const entries = readIndex();
  const next = entries.filter((e) => e.key !== key);
  if (next.length !== entries.length) writeIndex(next);
}

function isSensitiveKey(key: string): boolean {
  const lower = key.toLowerCase();
  return SENSITIVE_KEY_FRAGMENTS.some((frag) => lower.includes(frag));
}

/** Strip sensitive and explicitly-excluded fields from a values object before persisting. */
function sanitize<T extends Record<string, unknown>>(
  values: T,
  exclude: readonly string[],
): Partial<T> {
  const out: Partial<T> = {};
  for (const [k, v] of Object.entries(values)) {
    if (isSensitiveKey(k)) continue;
    if (exclude.includes(k)) continue;
    if (v === undefined) continue;
    (out as Record<string, unknown>)[k] = v;
  }
  return out;
}

// ── Low-level store (usable outside React) ──────────────────────────────────────────

export function readFormDraft<T extends Record<string, unknown>>(key: string): Partial<T> | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.localStorage.getItem(storageKey(key));
    if (!raw) return null;
    const parsed = JSON.parse(raw) as StoredDraft<T>;
    if (!parsed || parsed.version !== FORM_DRAFT_VERSION || typeof parsed.values !== "object") {
      // Stale/mismatched shape — discard the draft AND its resume-index entry together.
      window.localStorage.removeItem(storageKey(key));
      removeIndexEntry(key);
      return null;
    }
    return parsed.values;
  } catch {
    return null;
  }
}

export function writeFormDraft<T extends Record<string, unknown>>(
  key: string,
  values: T,
  exclude: readonly string[] = [],
  meta?: FormDraftMeta,
): void {
  if (typeof window === "undefined") return;
  const safe = sanitize(values, exclude);
  // Nothing worth persisting once secrets are stripped — don't leave an empty draft around.
  if (Object.keys(safe).length === 0) {
    clearFormDraft(key);
    return;
  }
  const updatedAt = Date.now();
  try {
    const payload: StoredDraft<T> = {
      version: FORM_DRAFT_VERSION,
      updatedAt,
      values: safe,
    };
    window.localStorage.setItem(storageKey(key), JSON.stringify(payload));
  } catch {
    // Best-effort — storage full or blocked (private mode). The form keeps its in-memory state.
    return;
  }
  // Register the draft in the resume index only when the caller gave a label+href to
  // navigate back to. No values are stored here — just the resume pointer.
  if (meta?.label && meta?.href) {
    upsertIndexEntry({ key, label: meta.label, href: meta.href, updatedAt });
  }
}

export function clearFormDraft(key: string): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.removeItem(storageKey(key));
  } catch {
    /* best-effort */
  }
  removeIndexEntry(key);
}

// ── Resume index (public API) ─────────────────────────────────────────────────────────

/**
 * List resumable drafts, newest-first. Self-healing: any index entry whose underlying
 * `exp:form_draft:<key>` no longer exists is dropped (and the pruned index rewritten),
 * so the shell never advertises a "resume" for work that isn't really stored.
 */
export function listResumableDrafts(): ResumableDraft[] {
  if (typeof window === "undefined") return [];
  const entries = readIndex();
  const live: ResumableDraft[] = [];
  let pruned = false;
  for (const entry of entries) {
    let exists = false;
    try {
      exists = window.localStorage.getItem(storageKey(entry.key)) !== null;
    } catch {
      exists = false;
    }
    if (exists) {
      live.push(entry);
    } else {
      pruned = true;
    }
  }
  if (pruned) writeIndex(live);
  return live.sort((a, b) => b.updatedAt - a.updatedAt);
}

// ── React hook ──────────────────────────────────────────────────────────────────────

export interface UseFormDraft<T extends Record<string, unknown>> {
  /** True once the initial localStorage read has run (avoids SSR/client hydration flash). */
  hydrated: boolean;
  /** The restored draft (sensitive fields already excluded), or null when none. */
  draft: Partial<T> | null;
  /** Snapshot the current field values (sanitised) to localStorage. */
  save: (values: T) => void;
  /** Clear the draft — call on successful submit. */
  clear: () => void;
}

/**
 * Snapshot/restore a form's field values across a sign-in interruption or session expiry.
 *
 * The caller passes the values it wants persisted on `save` and applies the returned
 * `draft` on mount. Sensitive fields are stripped regardless; pass `exclude` for any
 * additional non-secret fields you don't want carried (e.g. transient UI state).
 */
export function useFormDraft<T extends Record<string, unknown>>(
  key: string,
  options?: { exclude?: readonly string[]; label?: string; href?: string },
): UseFormDraft<T> {
  const excludeRef = useRef<readonly string[]>(options?.exclude ?? []);
  excludeRef.current = options?.exclude ?? [];
  // A label+href registers the draft in the resume index so the shell can offer to
  // continue it. Kept in a ref so `save` stays referentially stable.
  const metaRef = useRef<FormDraftMeta>({ label: options?.label, href: options?.href });
  metaRef.current = { label: options?.label, href: options?.href };

  const [hydrated, setHydrated] = useState(false);
  const [draft, setDraft] = useState<Partial<T> | null>(null);

  useEffect(() => {
    setDraft(readFormDraft<T>(key));
    setHydrated(true);
  }, [key]);

  const save = useCallback(
    (values: T) => {
      writeFormDraft<T>(key, values, excludeRef.current, metaRef.current);
    },
    [key],
  );

  const clear = useCallback(() => {
    clearFormDraft(key);
    setDraft(null);
  }, [key]);

  return { hydrated, draft, save, clear };
}

export interface UseResumableDrafts {
  /** Resumable drafts, newest-first. Empty until mounted (SSR-safe) and self-healing. */
  drafts: ResumableDraft[];
  /** Discard a draft entirely — clears both the stored values and its index entry. */
  discard: (key: string) => void;
}

/**
 * Surface the in-progress form drafts a user can resume. SSR-safe: returns an empty list
 * until mounted, then reads the self-healing index once on the client. `discard` really
 * removes the underlying draft — honesty guarantee: only genuinely stored work is listed.
 */
export function useResumableDrafts(): UseResumableDrafts {
  const [drafts, setDrafts] = useState<ResumableDraft[]>([]);

  useEffect(() => {
    setDrafts(listResumableDrafts());
  }, []);

  const discard = useCallback((key: string) => {
    clearFormDraft(key);
    setDrafts(listResumableDrafts());
  }, []);

  return { drafts, discard };
}
