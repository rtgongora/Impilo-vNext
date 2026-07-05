"use client";

/**
 * Clinical groups directory — /work/telemedicine/groups
 *
 * Groups are for DISCOVERY and ROUTING; session modes are for clinical
 * governance. The taxonomy and fail-closed creation governance are read from
 * the BFF operating-model registry
 * (`GET /internal/v1/telemedicine/operating-model/clinical-groups`) — the
 * same governed document a clinical-group backend must enforce. Creation
 * stays blocked with the reason stated; that fail-closed posture IS the
 * enforcement of "governance blocks unauthorised clinical group creation"
 * until the backend ships (HO-5).
 */

import Link from "next/link";
import { useState } from "react";
import { ArrowLeft, Lock, ShieldAlert, Users } from "lucide-react";
import { useClinicalGroupTaxonomy } from "@/hooks/queries/useTelemedicineOperatingModel";
import { getSessionMode } from "@/lib/telemedicine/session-modes";

const EXPOSURE_LABELS: Record<string, { label: string; className: string }> = {
  NEVER: { label: "No patient data", className: "bg-slate-100 text-slate-600 border-slate-200" },
  CASE_PRESENTATION_ONLY: {
    label: "Case presentation only",
    className: "bg-violet-50 text-violet-700 border-violet-200",
  },
  CARE_TEAM_SCOPED: {
    label: "Care-team scoped",
    className: "bg-sky-50 text-sky-700 border-sky-200",
  },
};

export default function ClinicalGroupsPage() {
  const [expanded, setExpanded] = useState<string | null>(null);
  const taxonomy = useClinicalGroupTaxonomy();
  const groupTypes = taxonomy.data?.groupTypes ?? [];
  const governanceRequirements = taxonomy.data?.governanceRequirements ?? [];

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-6">
      <header>
        <Link
          href="/work/telemedicine"
          className="mb-2 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-teal-700"
        >
          <ArrowLeft className="h-4 w-4" /> Telemedicine operating model
        </Link>
        <div className="flex items-center gap-3">
          <Users className="h-7 w-7 text-teal-600" />
          <div>
            <h1 className="text-2xl font-semibold text-slate-900">Clinical groups</h1>
            <p className="text-sm text-slate-500">
              Governed clinical routing objects — specialty pools, MDT panels, audit committees,
              on-call and diaspora pools. Not chat groups, and never wellness social groups.
            </p>
          </div>
        </div>
      </header>

      {/* Fail-closed creation notice */}
      <section className="rounded-lg border border-amber-200 bg-amber-50 p-5">
        <div className="mb-2 flex items-center gap-2 text-sm font-semibold text-amber-900">
          <Lock className="h-4 w-4" /> Group creation is governance-blocked
        </div>
        <p className="text-sm text-amber-800">
          {taxonomy.data?.creationBlockedReason ??
            "Creation is disabled while the governed taxonomy loads from the operating-model registry — the block never lifts on a failed read."}
        </p>
        {governanceRequirements.length > 0 ? (
          <div className="mt-3">
            <p className="mb-1 text-xs font-medium uppercase tracking-wide text-amber-700">
              Governance the backend must enforce before any group is creatable
            </p>
            <ul className="list-inside list-disc space-y-0.5 text-xs text-amber-800">
              {governanceRequirements.map((req) => (
                <li key={req}>{req}</li>
              ))}
            </ul>
          </div>
        ) : null}
      </section>

      {/* Taxonomy */}
      <section className="space-y-3">
        <h2 className="text-base font-semibold text-slate-800">
          Governed group taxonomy{groupTypes.length > 0 ? ` (${groupTypes.length} types)` : ""}
        </h2>
        {taxonomy.isLoading ? (
          <p className="rounded-lg border border-slate-200 bg-white p-6 text-sm text-slate-400">
            Loading the governed taxonomy from the operating-model registry…
          </p>
        ) : null}
        {taxonomy.isError || (!taxonomy.isLoading && groupTypes.length === 0) ? (
          <p className="rounded-lg border border-amber-200 bg-amber-50 p-6 text-sm text-amber-800">
            The operating-model registry is unreachable, so the taxonomy cannot be shown. Group
            creation remains blocked.
          </p>
        ) : null}
        <ul className="space-y-2">
          {groupTypes.map((group) => {
            const exposure = EXPOSURE_LABELS[group.patientDataExposure];
            const isOpen = expanded === group.type;
            return (
              <li key={group.type} className="rounded-lg border border-slate-200 bg-white shadow-sm">
                <button
                  onClick={() => setExpanded(isOpen ? null : group.type)}
                  className="flex w-full items-center justify-between gap-3 px-4 py-3 text-left"
                >
                  <div>
                    <p className="text-sm font-medium text-slate-800">{group.label}</p>
                    <p className="text-xs text-slate-500">{group.purpose}</p>
                  </div>
                  <span
                    className={`shrink-0 rounded-full border px-2.5 py-0.5 text-[11px] font-medium ${exposure.className}`}
                  >
                    {exposure.label}
                  </span>
                </button>
                {isOpen ? (
                  <div className="border-t border-slate-100 px-4 py-3">
                    <p className="mb-1 text-xs font-medium uppercase tracking-wide text-slate-400">
                      Examples
                    </p>
                    <ul className="mb-3 flex flex-wrap gap-2">
                      {group.examples.map((example) => (
                        <li
                          key={example}
                          className="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-0.5 text-xs text-slate-600"
                        >
                          {example}
                        </li>
                      ))}
                    </ul>
                    <p className="mb-1 text-xs font-medium uppercase tracking-wide text-slate-400">
                      Allowed session modes
                    </p>
                    <ul className="flex flex-wrap gap-2">
                      {group.allowedSessionModeIds.map((modeId) => {
                        const mode = getSessionMode(modeId);
                        return mode ? (
                          <li key={modeId}>
                            <Link
                              href="/work/telemedicine/session-modes"
                              className="rounded-full border border-teal-200 bg-teal-50 px-2.5 py-0.5 text-xs text-teal-700 hover:bg-teal-100"
                            >
                              {mode.name}
                            </Link>
                          </li>
                        ) : null;
                      })}
                    </ul>
                  </div>
                ) : null}
              </li>
            );
          })}
        </ul>
      </section>

      <section className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-xs text-slate-500">
        <div className="mb-1 flex items-center gap-2 font-medium text-slate-600">
          <ShieldAlert className="h-4 w-4" /> Why wellness social groups are not reused
        </div>
        The community-service social groups carry no consent model, no clinical audit trail, no
        cadre/eligibility rules and no session-mode limits. Clinical groups need Khuluma
        conversation links (referral/case-scoped) plus a net-new governed group model — recorded
        as handoff HO-5 in the operating-model capability map. Until then, request routing runs
        through the{" "}
        <Link href="/work/telemedicine/routing" className="text-teal-600 hover:underline">
          routing directory
        </Link>{" "}
        (person, specialty pool, workspace, facility service).
      </section>
    </div>
  );
}
