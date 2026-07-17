"use client";

/**
 * ResumeWorkSection — "Continue where you left off".
 *
 * Surfaces genuine in-progress form drafts (half-finished work the user can resume),
 * as registered in the form-draft resume index. This is DISTINCT from the shell's
 * "Recent" destinations (visited routes): these are unsaved work, not history.
 *
 * Honesty: the list comes straight from `useResumableDrafts()`, which self-heals against
 * the real underlying drafts — nothing is advertised that isn't truly stored. "Discard"
 * really clears the draft (values + index entry). Renders nothing when there is no work.
 */

import { useRouter } from "next/navigation";
import { History, ArrowRight, X } from "lucide-react";
import { useResumableDrafts } from "@/hooks/useFormDraftStore";
import { useShellStore } from "@/hooks/useShellStore";

/** Compact "updated X ago" hint. Purely presentational; no locale dependency. */
function relativeTime(updatedAt: number): string {
  const seconds = Math.max(0, Math.floor((Date.now() - updatedAt) / 1000));
  if (seconds < 60) return "just now";
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days}d ago`;
  const weeks = Math.floor(days / 7);
  return `${weeks}w ago`;
}

export function ResumeWorkSection() {
  const router = useRouter();
  const { drafts, discard } = useResumableDrafts();
  const setStartOpen = useShellStore((s) => s.setStartOpen);

  if (drafts.length === 0) return null;

  return (
    <section className="mb-4">
      <h3 className="mb-2 flex items-center gap-1.5 px-1 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">
        <History className="h-3 w-3" />
        Continue where you left off
      </h3>
      <ul className="space-y-1">
        {drafts.map((item) => (
          <li
            key={item.key}
            className="flex items-center gap-1 rounded-lg border border-transparent hover:border-border hover:bg-background dark:hover:border-border dark:hover:bg-primary-soft"
          >
            <button
              type="button"
              onClick={() => {
                router.push(item.href);
                setStartOpen(false);
              }}
              className="flex min-w-0 flex-1 items-center gap-2 px-2 py-2 text-left"
            >
              <ArrowRight className="h-4 w-4 shrink-0 text-primary" />
              <span className="min-w-0">
                <span className="block truncate text-sm font-medium text-foreground dark:text-foreground">
                  {item.label}
                </span>
                <span className="block truncate text-[11px] text-muted-foreground">
                  Continue · updated {relativeTime(item.updatedAt)}
                </span>
              </span>
            </button>
            <button
              type="button"
              className="mr-1 shrink-0 rounded-md p-2 text-muted-foreground hover:bg-primary-soft hover:text-foreground dark:hover:bg-neutral-900"
              title="Discard this draft"
              aria-label={`Discard "${item.label}"`}
              onClick={() => discard(item.key)}
            >
              <X className="h-4 w-4" />
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
