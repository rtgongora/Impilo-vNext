/**
 * Shared UI helpers for provider professional hub screens.
 */

type MinimalSection = { id?: unknown; title?: unknown };

/**
 * Which sections to show: the server's answer, or the local layout.
 *
 * <p>Every hub screen used to write `remote.length > 0 ? remote : FALLBACK_SECTIONS`, which
 * conflates two different states. The experience BFF now withholds sections whose routes the
 * caller's roles refuse, so an empty list is a real, correct answer — "nothing in this hub is
 * open to you". Treating it as a miss and substituting the hardcoded catalogue would hand back
 * the complete unfiltered list to precisely the callers the filter just protected, defeating it
 * exactly where it matters and leaving no trace.
 *
 * So the fallback is for "no answer", never for "an empty answer".
 */
export function resolveHubSections<T extends MinimalSection>(
  remote: T[] | undefined,
  fallback: T[],
): { sections: T[]; isOfflineLayout: boolean } {
  if (!remote) return { sections: fallback, isOfflineLayout: true };
  return { sections: remote.filter((s) => s?.id && s?.title), isOfflineLayout: false };
}

export function formatHubTimestamp(iso?: string | null): string | null {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  try {
    return d.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
  } catch {
    return null;
  }
}
