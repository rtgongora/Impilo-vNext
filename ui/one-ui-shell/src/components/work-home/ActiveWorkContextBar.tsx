"use client";

/**
 * ActiveWorkContextBar (Phase F8/F9) — shows the currently active resolved work context and
 * lets the person switch (and pick a mode when the context offers more than one).
 */

import { useState } from "react";
import { ChevronDown, Loader2 } from "lucide-react";
import { usePathname } from "next/navigation";
import { matchRouteDefinition } from "@/lib/routes";
import { useSessionExperienceContract } from "@/hooks/useSessionExperienceContract";
import { useWorkSessionStore } from "@/hooks/useWorkSessionStore";
import { useSwitchWorkContext } from "@/hooks/queries/useSwitchWorkContext";
import type { ResolvedWorkContextView } from "@/lib/trust";
import {
  describeWorkContextRestrictions,
  describeWorkMode,
} from "@/lib/work-home/restriction-copy";

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
  const session = useWorkSessionStore((s) => s.session);
  const switchWorkContext = useSwitchWorkContext();
  const [open, setOpen] = useState(false);
  const [switching, setSwitching] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [modePickerFor, setModePickerFor] = useState<string | null>(null);

  const routeInfo = pathname ? matchRouteDefinition(pathname) : null;
  const isWorkZone = routeInfo?.navZone === "work";

  const contexts = contract?.resolvedWorkContexts ?? [];
  // Active means minted — never treat recommendedContextId as selected without a token.
  const activeContextId = session?.contextId ?? undefined;
  const active = contexts.find((c) => c.contextId === activeContextId);
  const restrictionLines = describeWorkContextRestrictions(active?.restrictions);

  if (!isWorkZone || contexts.length === 0) {
    return null;
  }

  const groups = GROUP_ORDER.map((g) => ({ ...g, items: contexts.filter((c) => c.groupHint === g.key) })).filter(
    (g) => g.items.length > 0,
  );

  async function mint(context: ResolvedWorkContextView, mode: string) {
    if (!mode || switching) return;
    setSwitching(true);
    setError(null);
    try {
      await switchWorkContext(context, mode);
    } catch {
      setSwitching(false);
      setError("Could not switch workplace. Try again.");
      setModePickerFor(null);
    }
  }

  async function handleSelect(context: ResolvedWorkContextView) {
    if (context.contextId === activeContextId && !modePickerFor) {
      setOpen(false);
      return;
    }
    const modes = context.availableModes?.length
      ? context.availableModes
      : context.defaultMode
        ? [context.defaultMode]
        : [];
    if (modes.length === 0) {
      setError("That workplace has no permitted work mode.");
      return;
    }
    if (modes.length === 1) {
      await mint(context, modes[0]);
      return;
    }
    setModePickerFor(context.contextId);
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
        <span className="truncate">
          {active?.label ?? "Choose your workplace"}
          {session?.workMode ? ` · ${describeWorkMode(session.workMode)}` : ""}
        </span>
        <ChevronDown className="h-3.5 w-3.5 shrink-0" />
      </button>
      {restrictionLines.length > 0 && (
        <p className="truncate px-2 text-[10px] text-amber-700 dark:text-amber-400">
          {restrictionLines[0]}
        </p>
      )}

      {open && (
        <div className="absolute left-3 top-full z-30 mt-1 w-80 max-w-[90vw] rounded-lg border border-[color:var(--border-soft)] bg-[color:var(--surface)] p-2 shadow-lg">
          {error && (
            <p className="mb-2 rounded px-2 py-1 text-xs text-danger" role="alert">
              {error}
            </p>
          )}
          {groups.map((group) => (
            <div key={group.key} className="mb-2 last:mb-0">
              <p className="mb-1 px-1 text-[10px] font-semibold uppercase text-[color:var(--text-muted)]">
                {group.label}
              </p>
              {group.items.map((context) => (
                <div key={context.contextId}>
                  <button
                    type="button"
                    onClick={() => void handleSelect(context)}
                    className={`block w-full truncate rounded-md px-2 py-1.5 text-left text-xs transition ${
                      context.contextId === activeContextId
                        ? "bg-[color:var(--primary-soft)] font-medium text-[color:var(--primary-hover)]"
                        : "text-[color:var(--text-secondary)] hover:bg-[color:var(--surface-soft)]"
                    }`}
                  >
                    {context.label}
                  </button>
                  {modePickerFor === context.contextId && (
                    <div className="mb-1 flex flex-wrap gap-1 px-2 pb-1">
                      {(context.availableModes ?? []).map((mode) => (
                        <button
                          key={mode}
                          type="button"
                          disabled={switching}
                          onClick={() => void mint(context, mode)}
                          className="rounded border border-[color:var(--border-soft)] px-1.5 py-0.5 text-[10px] hover:bg-[color:var(--surface-soft)]"
                        >
                          {describeWorkMode(mode)}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              ))}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
