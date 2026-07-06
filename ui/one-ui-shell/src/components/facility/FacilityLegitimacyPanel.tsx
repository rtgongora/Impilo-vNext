"use client";

import Link from "next/link";
import { CheckCircle2, Clock, ShieldAlert, ShieldCheck, XCircle } from "lucide-react";
import type {
  FacilitySourceLegitimacyView,
  FacilityStatusComposite,
} from "@/hooks/queries/useFacilityStatusComposite";

/**
 * Facility legitimacy & platform-access panel (IATG Wave 2, WS-E).
 *
 * Renders the honest per-source legitimacy model side by side: the derived platform-access verdict,
 * every per-source verdict (HPA_LEGAL / MINISTRY_OPERATIONAL / PLATFORM_OPERATIONAL) with its status,
 * as-of, allowed-on-platform badge and reason, and a distinct treatment for the governed
 * GOVERNMENT_OPERATIONAL_EXCEPTION escape hatch (mandatory reason). Reuses the facility detail page's
 * CheckCircle2 / XCircle / Clock icon idiom.
 */

const LEGITIMACY_SOURCE_LABELS: Record<string, string> = {
  HPA_LEGAL: "Health Professions Authority (legal)",
  MINISTRY_OPERATIONAL: "Ministry of Health (operational)",
  PLATFORM_OPERATIONAL: "Impilo platform (operational)",
};

function legitimacyStatusIcon(status: string) {
  if (status === "REGISTERED_CURRENT") {
    return <CheckCircle2 className="w-4 h-4 text-green-600" />;
  }
  if (status === "PENDING_VERIFICATION") {
    return <Clock className="w-4 h-4 text-amber-500" />;
  }
  if (status === "GOVERNMENT_OPERATIONAL_EXCEPTION") {
    return <ShieldAlert className="w-4 h-4 text-amber-600" />;
  }
  return <XCircle className="w-4 h-4 text-red-400" />;
}

function LegitimacySourceRow({ view }: { view: FacilitySourceLegitimacyView }) {
  const isException = view.status === "GOVERNMENT_OPERATIONAL_EXCEPTION";
  const label = LEGITIMACY_SOURCE_LABELS[view.source] ?? view.source;
  return (
    <li
      className={`rounded-lg border p-3 ${
        isException ? "border-amber-300 bg-amber-50" : "border-border bg-background"
      }`}
      data-testid={`legitimacy-source-${view.source}`}
    >
      <div className="flex items-center justify-between gap-2">
        <span className="flex items-center gap-2 text-sm font-medium text-foreground">
          {legitimacyStatusIcon(view.status)}
          {label}
        </span>
        <span
          className={`inline-block px-2 py-0.5 text-xs rounded-full font-medium ${
            view.allowedOnPlatform ? "bg-green-100 text-green-700" : "bg-red-50 text-red-600"
          }`}
        >
          {view.allowedOnPlatform ? "Allowed" : "Not allowed"}
        </span>
      </div>
      <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-muted-foreground">
        <span>{view.status.replace(/_/g, " ").toLowerCase()}</span>
        {view.asOf ? <span>as of {new Date(view.asOf).toLocaleDateString()}</span> : null}
      </div>
      {isException ? (
        <p
          className="mt-2 rounded-md bg-amber-100 px-2 py-1 text-xs text-amber-900"
          data-testid={`legitimacy-exception-reason-${view.source}`}
        >
          <span className="font-medium">Government operational exception:</span>{" "}
          {view.reason || "Reason not recorded"}
        </p>
      ) : view.reason ? (
        <p className="mt-1 text-xs text-muted-foreground">{view.reason}</p>
      ) : null}
    </li>
  );
}

export function FacilityLegitimacyPanel({
  composite,
  facilityId,
}: {
  composite: FacilityStatusComposite;
  facilityId: string;
}) {
  return (
    <div
      className="bg-card rounded-lg border border-border p-5 space-y-4"
      data-testid="facility-legitimacy-panel"
    >
      <div className="flex items-center justify-between">
        <h3 className="font-medium text-foreground flex items-center gap-2">
          <ShieldCheck className="w-4 h-4 text-primary" /> Legitimacy & platform access
        </h3>
        <span
          className={`inline-flex items-center gap-1 px-2.5 py-1 text-xs rounded-full font-medium ${
            composite.platformAccessAllowed
              ? "bg-green-100 text-green-700"
              : "bg-red-50 text-red-600"
          }`}
          data-testid="facility-platform-access-verdict"
        >
          {composite.platformAccessAllowed ? (
            <CheckCircle2 className="w-3.5 h-3.5" />
          ) : (
            <ShieldAlert className="w-3.5 h-3.5" />
          )}
          {composite.platformAccessAllowed ? "Allowed on platform" : "Not allowed on platform"}
        </span>
      </div>

      {composite.regulatoryStatus && (
        <p className="text-xs text-muted-foreground">
          Regulatory status:{" "}
          <span className="font-medium text-foreground">
            {composite.regulatoryStatus.replace(/_/g, " ")}
          </span>
        </p>
      )}

      {composite.sourceLegitimacy.length > 0 ? (
        <ul className="grid grid-cols-1 md:grid-cols-2 gap-2">
          {composite.sourceLegitimacy.map((view) => (
            <LegitimacySourceRow key={view.source} view={view} />
          ))}
        </ul>
      ) : (
        <p className="text-sm text-muted-foreground">
          No per-source legitimacy verdicts recorded yet.
        </p>
      )}

      {composite.reasons.length > 0 && (
        <div>
          <p className="text-xs text-muted-foreground mb-1">Verdict reasons</p>
          <ul className="space-y-1">
            {composite.reasons.map((reason, i) => (
              <li key={i} className="text-xs text-muted-foreground">
                &middot; {reason}
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="border-t border-border pt-3">
        <Link
          href={`/facility/claim?facilityUuid=${encodeURIComponent(facilityId)}`}
          className="inline-flex items-center gap-1 text-sm font-medium text-primary hover:underline"
        >
          <ShieldCheck className="w-4 h-4" /> Claim administration
        </Link>
      </div>
    </div>
  );
}
