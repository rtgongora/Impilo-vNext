"use client";

/**
 * Request Work Access — Professional control layer.
 * Route: /professional/request-access
 *
 * Self-service seam for professionals without an active facility assignment
 * (hasProfessionalAccess && !hasWorkAccess): submits a provider-worker
 * onboarding access request against the existing admin-governance flow.
 * Approval stages the account; the facility admin completes activation via
 * the staff-assignments flow on /work/facility/[id]/staff-access.
 */

import { useMemo, useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { ArrowLeft, Building2, CheckCircle2, Clock, Send, ShieldQuestion } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { NompiloHint } from "@/components/intelligent/NompiloHint";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useIdentityContext } from "@/hooks/useIdentityContext";
import { useFacilities } from "@/hooks/queries/useFacilities";
import { submitOnboardingFlow } from "@/lib/admin-governance/api/onboardingApi";
import { listAccessRequests } from "@/lib/admin-governance/api/accessRequestsApi";
import { isActionResponse } from "@/lib/admin-governance/api/client";
import type { AccessRequest } from "@/lib/admin-governance/types";

const ROLE_TEMPLATE_SUGGESTIONS = [
  "facility_clinician",
  "facility_nurse",
  "facility_pharmacist",
  "facility_administrator",
  "community_health_worker",
];

function useMyAccessRequests(actorId: string | undefined) {
  return useQuery({
    queryKey: ["my-access-requests", actorId],
    queryFn: async () => {
      const result = await listAccessRequests();
      if (isActionResponse(result)) return [] as AccessRequest[];
      return result.filter((r) => actorId && r.requesterId === actorId);
    },
    enabled: Boolean(actorId),
  });
}

export default function RequestWorkAccessPage() {
  const { user } = useAuthStore();
  const identity = useIdentityContext();
  const actorId = user?.healthId?.trim() || user?.id;

  const [facilitySearch, setFacilitySearch] = useState("");
  const [facilityId, setFacilityId] = useState("");
  const [facilityName, setFacilityName] = useState("");
  const [roleTemplate, setRoleTemplate] = useState("facility_clinician");
  const [officialEmail, setOfficialEmail] = useState(user?.email ?? "");
  const [purpose, setPurpose] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [submitResult, setSubmitResult] = useState<{ ok: boolean; message: string } | null>(null);

  const { data: facilitiesData, isLoading: facilitiesLoading } = useFacilities(
    facilitySearch.trim().length >= 2 ? { search: facilitySearch.trim(), size: 8 } : { size: 8 },
  );
  const facilities = useMemo(
    () => (facilitiesData?.data ?? []).map((f) => ({ id: f.id, name: f.attributes.name, code: f.attributes.code, type: f.attributes.facilityType })),
    [facilitiesData],
  );

  const { data: myRequests = [], refetch: refetchMyRequests } = useMyAccessRequests(actorId);

  const canSubmit = Boolean(facilityId && roleTemplate.trim() && officialEmail.trim() && !submitting);

  async function handleSubmit() {
    if (!canSubmit) return;
    setSubmitting(true);
    setSubmitResult(null);
    try {
      const result = await submitOnboardingFlow("provider-worker", {
        roleTemplate: roleTemplate.trim(),
        requestedScope: facilityId,
        purpose: purpose.trim() || `Work access request for ${facilityName}`,
        profile: {
          officialEmail: officialEmail.trim(),
          displayName: user?.displayName ?? "",
          facilityId,
          facilityName,
          selfService: true,
        },
      });
      const ok = !isActionResponse(result) || result.status !== "denied";
      setSubmitResult(
        ok
          ? { ok: true, message: "Request submitted. A facility administrator will review it under Access Requests." }
          : { ok: false, message: result.friendlyMessage || result.blockedReason || "Submission failed." },
      );
      if (ok) void refetchMyRequests();
    } catch (e) {
      setSubmitResult({ ok: false, message: e instanceof Error ? e.message : "Submission failed." });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AppLayout>
      <div className="max-w-3xl mx-auto space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold text-foreground">Request Work Access</h1>
            <p className="mt-1 text-sm text-muted-foreground">
              Ask a facility administrator to activate your work assignment at a facility.
            </p>
          </div>
          <Link
            href="/professional"
            className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium text-muted-foreground bg-card border border-border rounded-lg hover:bg-background transition-colors"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            Professional Profile
          </Link>
        </div>

        {identity.hasWorkAccess ? (
          <div className="rounded-xl border border-border bg-success-soft px-4 py-3 text-sm text-foreground flex items-center gap-2">
            <CheckCircle2 className="h-4 w-4 text-emerald-600" />
            You already have an active work assignment. New requests here add access at another facility.
          </div>
        ) : null}

        <section className="bg-card rounded-xl border border-border shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-border bg-background">
            <h2 className="text-sm font-semibold text-foreground flex items-center gap-2">
              <Building2 className="h-4 w-4 text-primary" />
              Facility
            </h2>
          </div>
          <div className="px-6 py-4 space-y-3">
            <input
              type="text"
              value={facilitySearch}
              onChange={(e) => setFacilitySearch(e.target.value)}
              placeholder="Search facilities by name…"
              className="w-full rounded-lg border border-border px-3 py-2 text-sm bg-background"
            />
            <div className="divide-y divide-gray-100 rounded-lg border border-border">
              {facilitiesLoading ? (
                <div className="px-4 py-3 text-sm text-muted-foreground">Loading facilities…</div>
              ) : facilities.length === 0 ? (
                <div className="px-4 py-3 text-sm text-muted-foreground">No facilities match your search.</div>
              ) : (
                facilities.map((f) => (
                  <button
                    key={f.id}
                    type="button"
                    onClick={() => {
                      setFacilityId(f.id);
                      setFacilityName(f.name);
                    }}
                    className={[
                      "w-full px-4 py-2.5 flex items-center justify-between text-left transition-colors",
                      facilityId === f.id ? "bg-primary-soft" : "hover:bg-background",
                    ].join(" ")}
                  >
                    <span className="text-sm text-foreground">{f.name}</span>
                    <span className="text-xs text-muted-foreground font-mono">{f.code || f.type}</span>
                  </button>
                ))
              )}
            </div>
          </div>
        </section>

        <section className="bg-card rounded-xl border border-border shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-border bg-background">
            <h2 className="text-sm font-semibold text-foreground flex items-center gap-2">
              <ShieldQuestion className="h-4 w-4 text-primary" />
              Requested Role
            </h2>
          </div>
          <div className="px-6 py-4 space-y-3">
            <label className="block text-xs font-medium text-muted-foreground">
              Role template
              <input
                type="text"
                list="role-template-suggestions"
                value={roleTemplate}
                onChange={(e) => setRoleTemplate(e.target.value)}
                className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm bg-background"
              />
              <datalist id="role-template-suggestions">
                {ROLE_TEMPLATE_SUGGESTIONS.map((t) => (
                  <option key={t} value={t} />
                ))}
              </datalist>
            </label>
            <label className="block text-xs font-medium text-muted-foreground">
              Official email (used to stage your account on approval)
              <input
                type="email"
                value={officialEmail}
                onChange={(e) => setOfficialEmail(e.target.value)}
                className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm bg-background"
              />
            </label>
            <label className="block text-xs font-medium text-muted-foreground">
              Purpose (optional)
              <textarea
                value={purpose}
                onChange={(e) => setPurpose(e.target.value)}
                rows={2}
                placeholder="e.g. Starting duty at this facility from next week"
                className="mt-1 w-full rounded-lg border border-border px-3 py-2 text-sm bg-background"
              />
            </label>
            <div className="flex items-center gap-3 pt-1">
              <button
                type="button"
                disabled={!canSubmit}
                onClick={handleSubmit}
                className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
              >
                <Send className="w-3.5 h-3.5" />
                {submitting ? "Submitting…" : "Submit request"}
              </button>
              {submitResult ? (
                <p className={["text-sm", submitResult.ok ? "text-success-foreground" : "text-brand-red"].join(" ")}>
                  {submitResult.message}
                </p>
              ) : null}
            </div>
          </div>
        </section>

        <section className="bg-card rounded-xl border border-border shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-border bg-background">
            <h2 className="text-sm font-semibold text-foreground flex items-center gap-2">
              <Clock className="h-4 w-4 text-primary" />
              My Requests
            </h2>
          </div>
          <div className="divide-y divide-gray-100">
            {myRequests.length === 0 ? (
              <div className="px-6 py-5 text-sm text-muted-foreground text-center">No access requests yet.</div>
            ) : (
              myRequests.map((r) => (
                <div key={r.id} className="px-6 py-3 flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-foreground">{r.requestedRole ?? r.requestType}</p>
                    <p className="text-xs text-muted-foreground">
                      {r.requestedScope ?? "—"} · {r.createdAt ? new Date(r.createdAt).toLocaleString() : ""}
                    </p>
                  </div>
                  <span className="text-xs font-medium rounded-full border border-border px-2.5 py-1 text-muted-foreground uppercase">
                    {r.status}
                  </span>
                </div>
              ))
            )}
          </div>
        </section>
      </div>

      <NompiloHint message="Your request goes to the facility's administrators. Once approved and assigned, the Work tab appears on your home screen." />
    </AppLayout>
  );
}
