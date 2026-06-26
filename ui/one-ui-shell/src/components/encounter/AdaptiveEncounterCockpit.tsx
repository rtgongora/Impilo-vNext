"use client";

/**
 * Adaptive Encounter Cockpit (shared read-model C9).
 *
 * Renders the cockpit tab/action spine STRICTLY from the PCT Cadre Engine decision. Doctrine:
 *  - A disabled action is shown greyed-out with its reason (step-up / supervisor / not-permitted) — it is
 *    NEVER rendered as a live button (no dead buttons, no fake completions).
 *  - Authz-gated actions still call the existing Tshepo ext_authz path at execution; this component only
 *    drives visibility/enablement.
 *  - The cockpit does not hardcode tabs; the tab set is whatever the Cadre Engine returns for the context.
 */

import { useEffect, useMemo } from "react";
import { Loader2, Lock, ShieldAlert, UserCog } from "lucide-react";
import {
  useResolveCadreDecision,
  type CadreCockpitAction,
  type CadreCockpitTab,
  type CadreDecisionRequest,
} from "@/hooks/queries/useCadreDecision";

interface AdaptiveEncounterCockpitProps {
  request: CadreDecisionRequest;
  /** Called when a permitted action is invoked. The component itself performs no clinical writes. */
  onAction?: (action: string, tab: string, requiresStepUp: boolean) => void;
  /** Optional renderer for a tab's body (the actual panels); receives the resolved tab. */
  renderTabBody?: (tab: CadreCockpitTab) => React.ReactNode;
}

const TAB_LABELS: Record<string, string> = {
  OVERVIEW: "Overview",
  ASSESSMENT: "Assessment",
  PROBLEMS: "Problems",
  ORDERS_RESULTS: "Orders & Results",
  CARE: "Care",
  CONSULTS_REFERRALS: "Consults & Referrals",
  NOTES: "Notes",
  VISIT_OUTCOME: "Visit Outcome",
};

function actionLabel(action: string): string {
  return action
    .toLowerCase()
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

export function AdaptiveEncounterCockpit({
  request,
  onAction,
  renderTabBody,
}: AdaptiveEncounterCockpitProps) {
  const resolve = useResolveCadreDecision();
  const { mutate } = resolve;

  // Re-resolve whenever the load-bearing context inputs change.
  const requestKey = useMemo(
    () =>
      [
        request.role,
        request.cadre,
        request.scope,
        request.visitType,
        request.acuity,
        request.context,
        request.accessState,
        request.journeyId,
        request.encounterId,
      ].join("|"),
    [request],
  );

  useEffect(() => {
    mutate(request);
    // requestKey captures every meaningful field of `request`.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mutate, requestKey]);

  if (resolve.isPending && !resolve.data) {
    return (
      <div className="flex items-center gap-2 p-4 text-sm text-[var(--text-secondary,#64748b)]">
        <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
        Resolving permitted workflow…
      </div>
    );
  }

  if (resolve.isError) {
    return (
      <div
        role="alert"
        className="m-2 rounded-md border border-[var(--danger-border,#fecaca)] bg-[var(--danger-bg,#fef2f2)] p-3 text-sm text-[var(--danger-text,#b91c1c)]"
      >
        Could not resolve the encounter cockpit. Permitted actions are unavailable until this loads — no
        action is enabled by default.
      </div>
    );
  }

  const decision = resolve.data;
  if (!decision) {
    return null;
  }

  const supervisorSet = new Set(decision.escalation.supervisorRequiredFor);
  const enabledTabs = decision.cockpitSpine.filter((t) => t.enabled);

  return (
    <div className="adaptive-encounter-cockpit" data-audit-ref={decision.auditRef}>
      {decision.escalation.breakGlassAvailable && (
        <div className="mb-2 flex items-center gap-2 rounded-md border border-[var(--warning-border,#fde68a)] bg-[var(--warning-bg,#fffbeb)] px-3 py-2 text-xs text-[var(--warning-text,#92400e)]">
          <ShieldAlert className="h-4 w-4" aria-hidden />
          Break-glass is available for this presentation. Use is audited.
        </div>
      )}

      <nav
        aria-label="Encounter cockpit tabs"
        className="flex flex-wrap gap-1 border-b border-[var(--border,#e2e8f0)]"
      >
        {enabledTabs.map((tab) => (
          <span
            key={tab.tab}
            className="px-3 py-2 text-sm font-medium text-[var(--text-primary,#0f172a)]"
          >
            {TAB_LABELS[tab.tab] ?? tab.tab}
          </span>
        ))}
      </nav>

      <div className="space-y-4 pt-3">
        {enabledTabs.map((tab) => (
          <section key={tab.tab} aria-label={TAB_LABELS[tab.tab] ?? tab.tab}>
            <h3 className="mb-2 text-sm font-semibold text-[var(--text-primary,#0f172a)]">
              {TAB_LABELS[tab.tab] ?? tab.tab}
            </h3>
            <div className="flex flex-wrap gap-2">
              {tab.actions.map((action) => (
                <CockpitActionButton
                  key={action.action}
                  action={action}
                  supervisorRequired={supervisorSet.has(action.action)}
                  onInvoke={() => onAction?.(action.action, tab.tab, action.requiresStepUp)}
                />
              ))}
            </div>
            {renderTabBody?.(tab)}
          </section>
        ))}
      </div>
    </div>
  );
}

function CockpitActionButton({
  action,
  supervisorRequired,
  onInvoke,
}: {
  action: CadreCockpitAction;
  supervisorRequired: boolean;
  onInvoke: () => void;
}) {
  // Disabled actions are shown but inert, with the reason surfaced. Never a live dead button.
  if (!action.enabled) {
    const reason = supervisorRequired ? "Requires supervisor" : "Not permitted for your cadre";
    return (
      <span
        className="inline-flex items-center gap-1 rounded-md border border-[var(--border,#e2e8f0)] bg-[var(--surface-muted,#f8fafc)] px-3 py-1.5 text-xs text-[var(--text-disabled,#94a3b8)]"
        title={reason}
        aria-disabled="true"
      >
        {supervisorRequired ? <UserCog className="h-3.5 w-3.5" aria-hidden /> : <Lock className="h-3.5 w-3.5" aria-hidden />}
        {actionLabel(action.action)}
        <span className="sr-only"> — {reason}</span>
      </span>
    );
  }

  return (
    <button
      type="button"
      onClick={onInvoke}
      className="inline-flex items-center gap-1 rounded-md border border-[var(--primary-border,#bfdbfe)] bg-[var(--primary-bg,#eff6ff)] px-3 py-1.5 text-xs font-medium text-[var(--primary-text,#1d4ed8)] hover:bg-[var(--primary-bg-hover,#dbeafe)]"
    >
      {actionLabel(action.action)}
      {action.requiresStepUp && (
        <>
          <ShieldAlert className="h-3.5 w-3.5" aria-hidden />
          <span className="sr-only"> (step-up verification required)</span>
        </>
      )}
    </button>
  );
}
