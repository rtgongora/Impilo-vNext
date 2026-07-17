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

import { useCallback, useEffect, useRef, useState } from "react";

/** Bumped when the wrapper shape changes; a mismatch discards all stored drafts on read. */
export const FORM_DRAFT_VERSION = "2026-07-17";

const KEY_PREFIX = "exp:form_draft:";

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

function storageKey(key: string): string {
  return `${KEY_PREFIX}${key}`;
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
      window.localStorage.removeItem(storageKey(key));
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
): void {
  if (typeof window === "undefined") return;
  const safe = sanitize(values, exclude);
  // Nothing worth persisting once secrets are stripped — don't leave an empty draft around.
  if (Object.keys(safe).length === 0) {
    clearFormDraft(key);
    return;
  }
  try {
    const payload: StoredDraft<T> = {
      version: FORM_DRAFT_VERSION,
      updatedAt: Date.now(),
      values: safe,
    };
    window.localStorage.setItem(storageKey(key), JSON.stringify(payload));
  } catch {
    // Best-effort — storage full or blocked (private mode). The form keeps its in-memory state.
  }
}

export function clearFormDraft(key: string): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.removeItem(storageKey(key));
  } catch {
    /* best-effort */
  }
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
  options?: { exclude?: readonly string[] },
): UseFormDraft<T> {
  const excludeRef = useRef<readonly string[]>(options?.exclude ?? []);
  excludeRef.current = options?.exclude ?? [];

  const [hydrated, setHydrated] = useState(false);
  const [draft, setDraft] = useState<Partial<T> | null>(null);

  useEffect(() => {
    setDraft(readFormDraft<T>(key));
    setHydrated(true);
  }, [key]);

  const save = useCallback(
    (values: T) => {
      writeFormDraft<T>(key, values, excludeRef.current);
    },
    [key],
  );

  const clear = useCallback(() => {
    clearFormDraft(key);
    setDraft(null);
  }, [key]);

  return { hydrated, draft, save, clear };
}
