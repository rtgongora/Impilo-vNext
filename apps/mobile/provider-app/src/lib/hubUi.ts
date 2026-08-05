/**
 * Shared UI helpers for provider professional hub screens.
 */

import { reachableByRole, type RoleGuardedSection } from "./hubReachability";

export type ResolvedHubSection = RoleGuardedSection & {
  id: string;
  title: string;
  web_path: string;
  hint?: string | null;
};

/**
 * Which sections to show: the server's answer, or the local layout.
 *
 * Two rules, each of which was once broken in a way that handed back exactly the links the
 * system had just decided to withhold.
 *
 * 1. The fallback is for "no answer", never for "an empty answer". Every hub screen used to
 *    write `remote.length > 0 ? remote : FALLBACK_SECTIONS`. The experience BFF withholds
 *    sections whose routes the caller's roles refuse, so `[]` is a real and correct answer
 *    meaning "nothing in this hub is open to you" — and that line turned it into the complete
 *    unfiltered catalogue, for precisely the caller it had been withheld from.
 *
 * 2. The fallback is filtered too. Offline there is no BFF to do it, and the bundled layout was
 *    shown to everyone, so being disconnected quietly restored every refused link.
 *
 * The server's own sections are NOT re-filtered: they arrive already filtered, by a service that
 * evaluated the real token rather than whatever roles this client happens to hold.
 */
export function resolveHubSections(
  remote: readonly ResolvedHubSection[] | undefined,
  fallback: readonly ResolvedHubSection[],
  roles: readonly string[],
): { sections: ResolvedHubSection[]; isOfflineLayout: boolean } {
  if (!remote) return { sections: reachableByRole(fallback, roles), isOfflineLayout: true };
  return {
    sections: remote.filter((s) => s?.id && s?.title),
    isOfflineLayout: false,
  };
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
