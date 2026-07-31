"use client";

/**
 * Inline work-context chooser on /work when no duty session is minted.
 * Selection mints a duty token via useSwitchWorkContext — never React-state-only —
 * so the authority envelope matches the workplace the person just chose.
 *
 * Optional preferMode filters/highlights contexts that offer that WorkMode
 * (e.g. from /home Other Ways → /work?prefer_mode=VIRTUAL_CARE).
 */

import { useMemo, useState } from "react";
import { Loader2 } from "lucide-react";
import type { ResolvedWorkContextView } from "@/lib/trust";
import { useSwitchWorkContext } from "@/hooks/queries/useSwitchWorkContext";
import {
  describeWorkContextRestrictions,
  describeWorkMode,
} from "@/lib/work-home/restriction-copy";
import { WORK_MODES, type WorkMode } from "../../../../../contracts/trust/types/work-mode";

const GROUP_ORDER: Array<{ key: ResolvedWorkContextView["groupHint"]; label: string }> = [
  { key: "today", label: "Today" },
  { key: "regular", label: "My regular workplaces" },
  { key: "virtual", label: "Virtual work" },
  { key: "oversight", label: "Oversight roles" },
  { key: "other", label: "Other authorised workplaces" },
  { key: "personal", label: "Personal" },
];

export function parsePreferMode(raw: string | null | undefined): WorkMode | null {
  if (!raw) return null;
  return (WORK_MODES as readonly string[]).includes(raw) ? (raw as WorkMode) : null;
}

function contextOffersMode(ctx: ResolvedWorkContextView, mode: WorkMode): boolean {
  if (ctx.availableModes?.includes(mode)) return true;
  return ctx.defaultMode === mode;
}

interface WorkHomeWorkplacePickerProps {
  contexts: ResolvedWorkContextView[];
  /** Optional override — tests inject this; production uses the mint hook. */
  onSelect?: (context: ResolvedWorkContextView, mode: string) => Promise<void> | void;
  /** Canonical WorkMode from ?prefer_mode= — filters/highlights matching contexts. */
  preferMode?: string | null;
}

export function WorkHomeWorkplacePicker({
  contexts,
  onSelect,
  preferMode: preferModeRaw,
}: WorkHomeWorkplacePickerProps) {
  const preferMode = parsePreferMode(preferModeRaw);
  const switchWorkContext = useSwitchWorkContext();
  const [pendingKey, setPendingKey] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [showAll, setShowAll] = useState(false);

  const matchingContexts = useMemo(() => {
    if (!preferMode) return contexts;
    return contexts.filter((c) => contextOffersMode(c, preferMode));
  }, [contexts, preferMode]);

  const visibleContexts =
    preferMode && matchingContexts.length > 0 && !showAll ? matchingContexts : contexts;

  if (contexts.length === 0) {
    return (
      <div className="rounded-lg border border-border bg-card p-6 text-center">
        <p className="text-sm font-medium text-foreground">No work assignment found</p>
        <p className="mt-1 text-sm text-muted-foreground">
          You don&apos;t currently have an active work assignment, appointment, or support posting.
          If this seems wrong, contact your facility or organisation administrator.
        </p>
      </div>
    );
  }

  const groups = GROUP_ORDER.map((g) => ({
    ...g,
    items: visibleContexts.filter((c) => c.groupHint === g.key),
  })).filter((g) => g.items.length > 0);

  async function choose(context: ResolvedWorkContextView, mode: string) {
    if (!mode || pendingKey) return;
    const key = `${context.contextId}:${mode}`;
    setPendingKey(key);
    setError(null);
    try {
      if (onSelect) {
        await onSelect(context, mode);
      } else {
        await switchWorkContext(context, mode);
      }
    } catch {
      setError("Could not start a work session for that workplace. Try again, or pick a different one.");
      setPendingKey(null);
    }
  }

  function handleCardClick(ctx: ResolvedWorkContextView) {
    const modes = ctx.availableModes?.length
      ? ctx.availableModes
      : ctx.defaultMode
        ? [ctx.defaultMode]
        : [];
    if (modes.length === 0) {
      setError("This workplace has no permitted work mode right now.");
      return;
    }
    if (preferMode && modes.includes(preferMode)) {
      void choose(ctx, preferMode);
      return;
    }
    if (modes.length === 1) {
      void choose(ctx, modes[0]);
      return;
    }
    setExpandedId((id) => (id === ctx.contextId ? null : ctx.contextId));
  }

  return (
    <div className="space-y-5">
      <p className="text-sm text-muted-foreground">
        Choose where you&apos;re working right now. Starting a workplace issues a duty session for
        that place and mode — you can switch at any time.
      </p>
      {preferMode && (
        <div className="rounded-md border border-border bg-card px-3 py-2 text-sm">
          <p className="text-foreground">
            Looking for workplaces that grant{" "}
            <span className="font-medium">{describeWorkMode(preferMode)}</span>.
          </p>
          {matchingContexts.length === 0 ? (
            <p className="mt-1 text-xs text-muted-foreground">
              None of your assignments currently offer that mode. Showing all workplaces.
            </p>
          ) : matchingContexts.length < contexts.length && !showAll ? (
            <button
              type="button"
              className="mt-1 text-xs text-muted-foreground underline hover:text-foreground"
              onClick={() => setShowAll(true)}
            >
              Show all workplaces
            </button>
          ) : null}
        </div>
      )}
      {error && (
        <p className="rounded-md border border-danger/30 bg-danger-soft px-3 py-2 text-sm text-danger" role="alert">
          {error}
        </p>
      )}
      {groups.map((group) => (
        <div key={group.key}>
          <p className="mb-2 text-xs font-semibold uppercase text-muted-foreground">{group.label}</p>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            {group.items.map((ctx) => {
              const modes = ctx.availableModes?.length
                ? ctx.availableModes
                : ctx.defaultMode
                  ? [ctx.defaultMode]
                  : [];
              const restrictionLines = describeWorkContextRestrictions(ctx.restrictions);
              const busy = pendingKey?.startsWith(`${ctx.contextId}:`) ?? false;
              const expanded = expandedId === ctx.contextId;
              const preferred = preferMode ? contextOffersMode(ctx, preferMode) : false;

              return (
                <div
                  key={ctx.contextId}
                  className={`rounded-lg border bg-card text-sm transition hover:border-primary ${
                    preferred ? "border-primary ring-1 ring-primary/30" : "border-border"
                  }`}
                >
                  <button
                    type="button"
                    disabled={!!pendingKey}
                    onClick={() => handleCardClick(ctx)}
                    className="flex w-full flex-col items-start gap-1 p-3 text-left disabled:opacity-60"
                  >
                    <span className="flex items-center gap-2 font-medium text-foreground">
                      {busy && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
                      {ctx.label}
                    </span>
                    {ctx.defaultMode && modes.length === 1 && (
                      <span className="text-xs text-muted-foreground">
                        {describeWorkMode(ctx.defaultMode)}
                      </span>
                    )}
                    {restrictionLines.map((line) => (
                      <span
                        key={line}
                        className="text-xs text-amber-700 dark:text-amber-400"
                      >
                        {line}
                      </span>
                    ))}
                    {modes.length > 1 && !expanded && (
                      <span className="text-xs text-muted-foreground">
                        {modes.length} modes — choose one
                      </span>
                    )}
                  </button>
                  {expanded && modes.length > 1 && (
                    <div className="flex flex-wrap gap-1.5 border-t border-border px-3 py-2">
                      {modes.map((mode) => (
                        <button
                          key={mode}
                          type="button"
                          disabled={!!pendingKey}
                          onClick={() => void choose(ctx, mode)}
                          className={`rounded-md border px-2 py-1 text-xs hover:bg-background disabled:opacity-60 ${
                            preferMode === mode ? "border-primary font-medium" : "border-border"
                          }`}
                        >
                          {pendingKey === `${ctx.contextId}:${mode}` ? (
                            <Loader2 className="inline h-3 w-3 animate-spin" />
                          ) : (
                            describeWorkMode(mode)
                          )}
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      ))}
    </div>
  );
}
