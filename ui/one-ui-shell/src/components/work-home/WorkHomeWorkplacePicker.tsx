"use client";

/**
 * Inline work-context chooser rendered directly on /work when no single context is
 * recommended (F3) — deliberately NOT a redirect to /facility, because oversight,
 * programme, regulatory and support contexts have no facility at all.
 *
 * Renders `contract.resolvedWorkContexts` (Phase C) grouped by `groupHint`, exactly the
 * switcher grouping the plan specifies for ActiveWorkContextBar (F8) — this component is
 * the in-page equivalent for first landing, before that bar exists.
 */

import type { ResolvedWorkContextView } from "@/lib/trust";

const GROUP_ORDER: Array<{ key: ResolvedWorkContextView["groupHint"]; label: string }> = [
  { key: "today", label: "Today" },
  { key: "regular", label: "My regular workplaces" },
  { key: "virtual", label: "Virtual work" },
  { key: "oversight", label: "Oversight roles" },
  { key: "other", label: "Other authorised workplaces" },
  { key: "personal", label: "Personal" },
];

interface WorkHomeWorkplacePickerProps {
  contexts: ResolvedWorkContextView[];
  onSelect: (contextId: string, mode: string | null) => void;
}

export function WorkHomeWorkplacePicker({ contexts, onSelect }: WorkHomeWorkplacePickerProps) {
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
    items: contexts.filter((c) => c.groupHint === g.key),
  })).filter((g) => g.items.length > 0);

  return (
    <div className="space-y-5">
      <p className="text-sm text-muted-foreground">
        Choose where you&apos;re working right now. You can switch at any time.
      </p>
      {groups.map((group) => (
        <div key={group.key}>
          <p className="mb-2 text-xs font-semibold uppercase text-muted-foreground">{group.label}</p>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            {group.items.map((ctx) => (
              <button
                key={ctx.contextId}
                type="button"
                onClick={() => onSelect(ctx.contextId, ctx.defaultMode ?? null)}
                className="flex flex-col items-start gap-1 rounded-lg border border-border bg-card p-3 text-left text-sm transition hover:border-primary hover:bg-background"
              >
                <span className="font-medium text-foreground">{ctx.label}</span>
                {ctx.restrictions.length > 0 && (
                  <span className="text-xs text-amber-600 dark:text-amber-400">
                    {ctx.restrictions.join(", ")}
                  </span>
                )}
              </button>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
