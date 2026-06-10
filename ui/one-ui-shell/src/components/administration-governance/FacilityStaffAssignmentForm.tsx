"use client";

import { useEffect, useMemo, useState } from "react";
import { GovernanceActionResult } from "./GovernanceActionResult";
import { OpaPrecheckPanel, submitDisabledFromPrecheck } from "./OpaPrecheckPanel";
import { useCreateFacilityStaffAssignment } from "@/hooks/useAdminGovernance";
import {
  eligibilityBlocksAssignment,
  getProviderWorkerEligibility,
  searchProviderWorkers,
} from "@/lib/admin-governance/api/providerWorkerApi";
import { runOpaPrecheck } from "@/lib/admin-governance/api";
import { isActionResponse, isPendingBackend } from "@/lib/admin-governance/api/client";
import type { AdminGovernanceActionResponse, LookupEnvelope, ProviderWorkerEligibility } from "@/lib/admin-governance/types";

interface FacilityStaffAssignmentFormProps {
  facilityId: string;
}

export function FacilityStaffAssignmentForm({ facilityId }: FacilityStaffAssignmentFormProps) {
  const [query, setQuery] = useState("");
  const [providerWorkerId, setProviderWorkerId] = useState("");
  const [departmentId, setDepartmentId] = useState("");
  const [roleTemplate, setRoleTemplate] = useState("");
  const [assumptionOfDutyRequired, setAssumptionOfDutyRequired] = useState(true);
  const [searchResults, setSearchResults] = useState<Record<string, unknown>[]>([]);
  const [lookupUnavailable, setLookupUnavailable] = useState(false);
  const [eligibility, setEligibility] = useState<ProviderWorkerEligibility | null>(null);
  const [precheck, setPrecheck] = useState<AdminGovernanceActionResponse | null>(null);
  const [precheckLoading, setPrecheckLoading] = useState(false);
  const [submitResult, setSubmitResult] = useState<AdminGovernanceActionResponse | null>(null);
  const submitMutation = useCreateFacilityStaffAssignment();

  useEffect(() => {
    async function loadSearch() {
      if (!query && !providerWorkerId) return;
      const response = await searchProviderWorkers({ q: query, providerWorkerId });
      if (isActionResponse(response) && isPendingBackend(response)) {
        setLookupUnavailable(true);
        setSearchResults([]);
        return;
      }
      const envelope = response as LookupEnvelope<{ items: Record<string, unknown>[] }>;
      setLookupUnavailable(envelope.integrationStatus === "pending_backend");
      setSearchResults(envelope.data?.items ?? []);
    }
    void loadSearch();
  }, [query, providerWorkerId]);

  useEffect(() => {
    async function loadEligibility() {
      if (!providerWorkerId) {
        setEligibility(null);
        return;
      }
      const response = await getProviderWorkerEligibility(providerWorkerId);
      if (isActionResponse(response) && isPendingBackend(response)) {
        setLookupUnavailable(true);
        setEligibility(null);
        return;
      }
      const envelope = response as LookupEnvelope<{ eligibility: ProviderWorkerEligibility }>;
      setLookupUnavailable(envelope.integrationStatus === "pending_backend");
      setEligibility(envelope.data?.eligibility ?? null);
    }
    void loadEligibility();
  }, [providerWorkerId]);

  useEffect(() => {
    async function loadPrecheck() {
      if (!providerWorkerId) return;
      setPrecheckLoading(true);
      try {
        const result = await runOpaPrecheck({
          requestedAction: "FACILITY_STAFF_ASSIGN",
          sourcePage: `/work/facility/${facilityId}/staff-access/add`,
          targetSubjectId: providerWorkerId,
          roleTemplate,
          requestedScope: departmentId,
          context: { facilityId, eligibility },
        });
        setPrecheck(result);
      } finally {
        setPrecheckLoading(false);
      }
    }
    void loadPrecheck();
  }, [providerWorkerId, roleTemplate, departmentId, facilityId, eligibility]);

  const submitDisabled = useMemo(() => {
    if (!providerWorkerId || !roleTemplate || lookupUnavailable) return true;
    if (eligibilityBlocksAssignment(eligibility)) return true;
    return submitDisabledFromPrecheck(precheck);
  }, [providerWorkerId, roleTemplate, lookupUnavailable, eligibility, precheck]);

  async function handleSubmit() {
    const result = await submitMutation.mutateAsync({
      facilityId,
      providerWorkerId,
      departmentId,
      roleTemplate,
      assumptionOfDutyRequired,
    });
    setSubmitResult(result);
  }

  const council = eligibility?.councilStatusSummary ?? {};
  const hsc = eligibility?.hscEmploymentSummary ?? {};

  return (
    <div className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div>
        <h3 className="text-base font-semibold text-slate-900">Assign Verified Provider / Worker</h3>
        <p className="mt-1 text-sm text-slate-600">
          Facility managers search verified providers — they cannot create Provider IDs here.
        </p>
      </div>

      <div className="grid gap-3 md:grid-cols-2">
        <label className="text-sm text-slate-700 md:col-span-2">
          Search provider / worker
          <input
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Name, Health ID, Provider ID"
          />
        </label>
        {searchResults.slice(0, 5).map((item, index) => {
          const id = String(item.providerId ?? item.id ?? item.providerWorkerId ?? index);
          return (
            <button
              key={id}
              type="button"
              onClick={() => setProviderWorkerId(id)}
              className="rounded-lg border border-slate-200 px-3 py-2 text-left text-sm hover:border-indigo-300"
            >
              {String(item.displayName ?? item.fullName ?? id)}
            </button>
          );
        })}
        <label className="text-sm text-slate-700">
          Provider / Worker ID
          <input
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
            value={providerWorkerId}
            onChange={(event) => setProviderWorkerId(event.target.value)}
            placeholder="P-..."
          />
        </label>
        <label className="text-sm text-slate-700">
          Department / unit
          <input
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
            value={departmentId}
            onChange={(event) => setDepartmentId(event.target.value)}
          />
        </label>
        <label className="text-sm text-slate-700">
          Role template
          <input
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
            value={roleTemplate}
            onChange={(event) => setRoleTemplate(event.target.value)}
            placeholder="facility_clinician"
          />
        </label>
        <label className="flex items-center gap-2 text-sm text-slate-700 md:col-span-2">
          <input
            type="checkbox"
            checked={assumptionOfDutyRequired}
            onChange={(event) => setAssumptionOfDutyRequired(event.target.checked)}
          />
          Assumption of duty required before Work activates
        </label>
      </div>

      {lookupUnavailable ? (
        <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950">
          Provider/Worker lookup is not currently available; assignment cannot be completed until professional eligibility can be verified.
        </div>
      ) : null}

      {eligibility ? (
        <div className="space-y-3">
          <Section title="Provider / Worker identity">
            <Info label="Name" value={eligibility.fullName} />
            <Info label="Provider / Worker ID" value={eligibility.providerWorkerId} />
            <Info label="Health ID" value={eligibility.linkedHealthId} />
            <Info label="Profession / cadre" value={`${eligibility.profession ?? ""} / ${eligibility.cadre ?? ""}`} />
            <Info label="Professional status" value={eligibility.professionalStatus} />
          </Section>
          <Section title="Professional / council status">
            <Info label="Council" value={String(council.councilName ?? council.councilCode ?? "")} />
            <Info label="Registration number" value={String(council.registrationNumber ?? "")} />
            <Info label="Licence status" value={String(council.licenceStatus ?? eligibility.licenceStatus ?? "")} />
            <Info label="Restrictions" value={String(council.restrictionStatus ?? "")} />
            <Info label="Expiry" value={String(council.expiryDate ?? "")} />
          </Section>
          <Section title="HSC / public-sector status">
            <Info label="Employment status" value={String(hsc.employmentStatus ?? "not applicable")} />
            <Info label="Posting" value={String(hsc.currentPostingFacility ?? hsc.postTitle ?? "")} />
            <Info label="Grade / post" value={String(hsc.grade ?? hsc.postTitle ?? "")} />
            <Info label="Transfer state" value={String(hsc.transferStatus ?? "")} />
            <Info label="Disciplinary status" value={String(hsc.disciplinaryEmploymentStatus ?? "")} />
          </Section>
          <Section title="Training / Fundo warnings">
            {(eligibility.trainingWarnings ?? eligibility.eligibilityWarnings ?? []).length ? (
              <ul className="list-disc pl-5 text-xs text-amber-900">
                {(eligibility.trainingWarnings ?? eligibility.eligibilityWarnings ?? []).map((warning) => (
                  <li key={warning}>{warning}</li>
                ))}
              </ul>
            ) : (
              <p className="text-xs text-slate-600">No training warnings returned.</p>
            )}
          </Section>
          {(eligibility.blockedReasons ?? []).length ? (
            <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-xs text-rose-950">
              {(eligibility.blockedReasons ?? []).map((reason) => (
                <p key={reason}>{reason}</p>
              ))}
            </div>
          ) : null}
        </div>
      ) : null}

      <OpaPrecheckPanel result={precheck} isLoading={precheckLoading} />
      {submitResult ? <GovernanceActionResult result={submitResult} /> : null}

      <button
        type="button"
        disabled={submitDisabled || submitMutation.isPending}
        onClick={() => void handleSubmit()}
        className="rounded-lg bg-indigo-700 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-800 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {submitMutation.isPending ? "Submitting assignment…" : "Submit assignment / access request"}
      </button>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="rounded-xl border border-slate-100 bg-slate-50 px-4 py-3">
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{title}</p>
      <div className="mt-2 grid gap-2 md:grid-cols-2">{children}</div>
    </div>
  );
}

function Info({ label, value }: { label: string; value?: string }) {
  return (
    <div className="text-xs text-slate-700">
      <span className="font-medium text-slate-900">{label}: </span>
      {value || "—"}
    </div>
  );
}
