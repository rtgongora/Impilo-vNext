"use client";

/**
 * ActiveWorkContextBar (Phase F8/F9) — shows the currently active resolved work context and
 * lets the person switch. Mounted inside AppLayout, gated on `navZone === "work"` for the
 * current pathname (matchRouteDefinition(pathname).navZone) — not ShellChrome, which also
 * renders on /home and /welcome, and not a work/layout.tsx wrapper, since only some /work/*
 * pages render AppLayout at all.
 *
 * Uses useSwitchWorkContext (F9) — a full mint + queryClient.clear() + sessionStorage sweep +
 * navigate, never invalidateQueries, so the previous context's cached data is never briefly
 * rendered under the new context's chrome.
 */

import { useState } from "react";
import { ChevronDown, Loader2 } from "lucide-react";
import { usePathname } from "next/navigation";
import { matchRouteDefinition } from "@/lib/routes";
import { useSessionExperienceContract } from "@/hooks/useSessionExperienceContract";
import { useSwitchWorkContext } from "@/hooks/queries/useSwitchWorkContext";
import type { ResolvedWorkContextView } from "@/lib/trust";

const GROUP_ORDER: Array<{ key: ResolvedWorkContextView["groupHint"]; label: string }> = [
  { key: "today", label: "Today" },
  { key: "regular", label: "My regular workplaces" },
  { key: "virtual", label: "Virtual work" },
  { key: "oversight", label: "Oversight roles" },
  { key: "other", label: "Other authorised workplaces" },
  { key: "personal", label: "Personal" },
];

export function ActiveWorkContextBar() {
  const pathname = usePathname();
  const { contract } = useSessionExperienceContract();
  const switchWorkContext = useSwitchWorkContext();
  const [open, setOpen] = useState(false);
  const [switching, setSwitching] = useState(false);

  const routeInfo = pathname ? matchRouteDefinition(pathname) : null;
  const isWorkZone = routeInfo?.navZone === "work";

  const contexts = contract?.resolvedWorkContexts ?? [];
  const activeContextId = contract?.recommendedContextId ?? undefined;
  const active = contexts.find((c) => c.contextId === activeContextId);

  if (!isWorkZone || contexts.length === 0) {
    return null;
  }

  const groups = GROUP_ORDER.map((g) => ({ ...g, items: contexts.filter((c) => c.groupHint === g.key) })).filter(
    (g) => g.items.length > 0,
  );

  async function handleSelect(context: ResolvedWorkContextView) {
    if (context.contextId === activeContextId || switching) {
      setOpen(false);
      return;
    }
    setSwitching(true);
    try {
      await switchWorkContext(context, context.defaultMode ?? context.availableModes[0]);
      // switchWorkContext navigates via window.location.assign on success — this component
      // unmounts on the ensuing full-page load, so there is no "finally" reset of `switching`.
    } catch {
      setSwitching(false);
      setOpen(false);
    }
  }

  return (
    <div className="relative border-b border-[color:var(--border-soft)] bg-[color:var(--surface)]/70 px-3 py-1.5 sm:px-4">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        disabled={switching}
        data-testid="active-work-context-trigger"
        className="inline-flex max-w-full items-center gap-1.5 rounded-lg px-2 py-1 text-xs font-medium text-[color:var(--text-secondary)] transition hover:bg-[color:var(--surface-soft)] disabled:opacity-60"
      >
        {switching ? <Loader2 className="h-3.5 w-3.5 animate-spin shrink-0" /> : null}
        <span className="truncate">{active?.label ?? "Choose your workplace"}</span>
        <ChevronDown className="h-3.5 w-3.5 shrink-0" />
      </button>

      {open && (
        <div className="absolute left-3 top-full z-30 mt-1 w-80 max-w-[90vw] rounded-lg border border-[color:var(--border-soft)] bg-[color:var(--surface)] p-2 shadow-lg">
          {groups.map((group) => (
            <div key={group.key} className="mb-2 last:mb-0">
              <p className="mb-1 px-1 text-[10px] font-semibold uppercase text-[color:var(--text-muted)]">
                {group.label}
              </p>
              {group.items.map((context) => (
                <button
                  key={context.contextId}
                  type="button"
                  onClick={() => handleSelect(context)}
                  className={`block w-full truncate rounded-md px-2 py-1.5 text-left text-xs transition ${
                    context.contextId === activeContextId
                      ? "bg-[color:var(--primary-soft)] font-medium text-[color:var(--primary-hover)]"
                      : "text-[color:var(--text-secondary)] hover:bg-[color:var(--surface-soft)]"
                  }`}
                >
                  {context.label}
                </button>
              ))}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
