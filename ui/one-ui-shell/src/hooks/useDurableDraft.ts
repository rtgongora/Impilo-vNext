"use client";

/**
 * TM-B11 (B11-2): local durable draft — journey #34 (browser refresh / crash during a draft).
 *
 * Persists a serializable snapshot of in-progress form content on THIS DEVICE (localStorage),
 * debounced, so a refresh or crash mid-composition loses nothing even before the server draft
 * exists. Restores once on mount (only when meaningful content is present), exposes the captured
 * timestamp for a stale banner, and clears on successful submit. Device-local convenience only —
 * the server draft remains the durable truth once created; never store secrets here.
 */

import { useCallback, useEffect, useRef, useState } from "react";

interface StoredDraft<T> {
  savedAt: string;
  data: T;
}

export function useDurableDraft<T>(
  key: string,
  snapshot: T,
  restore: (saved: T) => void,
  hasContent: (data: T) => boolean,
) {
  const [restoredAt, setRestoredAt] = useState<string | null>(null);
  const restoredRef = useRef(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Restore once on mount.
  useEffect(() => {
    if (restoredRef.current) return;
    restoredRef.current = true;
    try {
      const raw = window.localStorage.getItem(key);
      if (!raw) return;
      const parsed = JSON.parse(raw) as StoredDraft<T>;
      if (parsed && parsed.data && hasContent(parsed.data)) {
        restore(parsed.data);
        setRestoredAt(parsed.savedAt ?? null);
      }
    } catch {
      /* corrupt draft — ignore */
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  // Debounced autosave on change.
  useEffect(() => {
    if (timerRef.current) clearTimeout(timerRef.current);
    timerRef.current = setTimeout(() => {
      try {
        if (hasContent(snapshot)) {
          window.localStorage.setItem(
            key,
            JSON.stringify({ savedAt: new Date().toISOString(), data: snapshot } satisfies StoredDraft<T>),
          );
        }
      } catch {
        /* storage full/unavailable — draft is best-effort */
      }
    }, 600);
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, JSON.stringify(snapshot)]);

  const clear = useCallback(() => {
    try {
      window.localStorage.removeItem(key);
    } catch {
      /* ignore */
    }
    setRestoredAt(null);
  }, [key]);

  return { restoredAt, clear };
}
