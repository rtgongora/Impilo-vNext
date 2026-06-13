"use client";

import { useState } from "react";
import Link from "next/link";
import { Landmark, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useApproveEmergencyRedistribution,
  useEmergencyRedistributions,
  useInitiateEmergencyHandoff,
  useMadiCentralBankMetrics,
  useRequestEmergencyRedistribution,
} from "@/hooks/queries/useMadi";

const COMPONENT_TYPES = ["WHOLE_BLOOD", "RED_CELLS", "PLATELETS", "PLASMA", "CRYOPRECIPITATE"];
const BLOOD_GROUPS = ["O", "A", "B", "AB"];

export default function CentralBankPage() {
  const user = useAuthStore((s) => s.user);
  const { data, isPending, isError } = useMadiCentralBankMetrics();
  const redistributions = useEmergencyRedistributions();
  const requestRedistribution = useRequestEmergencyRedistribution();
  const approveRedistribution = useApproveEmergencyRedistribution();
  const initiateHandoff = useInitiateEmergencyHandoff();

  const [actionMessage, setActionMessage] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const [sourceFacilityId, setSourceFacilityId] = useState("");
  const [targetFacilityId, setTargetFacilityId] = useState("");
  const [bloodGroup, setBloodGroup] = useState("O");
  const [componentType, setComponentType] = useState("RED_CELLS");
  const [unitsRequested, setUnitsRequested] = useState("2");
  const [reason, setReason] = useState("");

  function handleRequest() {
    if (!sourceFacilityId.trim() || !targetFacilityId.trim()) return;
    setActionMessage(null);
    setActionError(null);
    requestRedistribution.mutate(
      {
        source_facility_id: sourceFacilityId.trim(),
        target_facility_id: targetFacilityId.trim(),
        blood_group: bloodGroup,
        component_type: componentType,
        units_requested: Number(unitsRequested),
        reason: reason.trim() || undefined,
        requested_by: user?.displayName ?? user?.id,
        priority: "EMERGENCY",
      },
      {
        onSuccess: () => setActionMessage("Emergency redistribution requested."),
        onError: () => setActionError("Request failed — verify admin role and facility IDs."),
      },
    );
  }

  function handleApprove(redistributionId: string, units: number) {
    setActionMessage(null);
    setActionError(null);
    approveRedistribution.mutate(
      {
        redistributionId,
        approved_by: user?.displayName ?? user?.id ?? "central-ops",
        units_allocated: units,
        status: "FULFILLED",
      },
      {
        onSuccess: () => setActionMessage("Redistribution approved and fulfilled."),
        onError: () => setActionError("Approval failed."),
      },
    );
  }

  function handleRetryHandoff(redistributionId: string) {
    setActionMessage(null);
    setActionError(null);
    initiateHandoff.mutate(
      {
        redistributionId,
        initiated_by: user?.displayName ?? user?.id ?? "central-ops",
      },
      {
        onSuccess: () => setActionMessage("NHUME handoff retry initiated."),
        onError: () => setActionError("Handoff retry failed."),
      },
    );
  }

  return (
    <AppLayout>
      <PageShell title="Central Blood Bank" subtitle="National inventory and haemovigilance oversight" icon={<Landmark className="h-6 w-6" />}>
        {isPending && (
          <div className="flex items-center gap-2 text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading central metrics…
          </div>
        )}

        {isError && (
          <div className="rounded-xl border border-danger/28 bg-danger-soft p-4 text-sm text-rose-800">
            Central metrics unavailable. Verify admin role and retry.
          </div>
        )}

        {data && (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
            {[
              { label: "Total units", value: data.totalUnits },
              { label: "Available units", value: data.availableUnits },
              { label: "Open haemovigilance cases", value: data.openHaemovigilanceCases },
            ].map(({ label, value }) => (
              <div key={label} className="rounded-2xl border border-border bg-card p-5">
                <p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p>
                <p className="mt-2 text-3xl font-semibold text-foreground">{String(value ?? 0)}</p>
              </div>
            ))}
          </div>
        )}

        {actionMessage ? (
          <div className="mb-4 rounded-xl border border-success/25 bg-success-soft px-4 py-3 text-sm text-primary-hover">
            {actionMessage}
          </div>
        ) : null}
        {actionError ? (
          <div className="mb-4 rounded-xl border border-danger/28 bg-danger-soft px-4 py-3 text-sm text-rose-800">
            {actionError}
          </div>
        ) : null}

        <section className="rounded-2xl border border-danger/28 bg-danger-soft/40 p-5 mb-6 space-y-4">
          <h2 className="text-sm font-semibold text-danger">Emergency redistribution</h2>
          <p className="text-xs text-muted-foreground">
            Request governed emergency allocation between facilities. Approvals are audited and emitted to the MADI event stream.
          </p>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <input
              value={sourceFacilityId}
              onChange={(e) => setSourceFacilityId(e.target.value)}
              placeholder="Source facility UUID"
              className="rounded-xl border border-border px-3 py-2 text-sm font-mono"
            />
            <input
              value={targetFacilityId}
              onChange={(e) => setTargetFacilityId(e.target.value)}
              placeholder="Target facility UUID"
              className="rounded-xl border border-border px-3 py-2 text-sm font-mono"
            />
            <select value={bloodGroup} onChange={(e) => setBloodGroup(e.target.value)} className="rounded-xl border border-border px-3 py-2 text-sm bg-card">
              {BLOOD_GROUPS.map((g) => <option key={g} value={g}>{g}</option>)}
            </select>
            <select value={componentType} onChange={(e) => setComponentType(e.target.value)} className="rounded-xl border border-border px-3 py-2 text-sm bg-card">
              {COMPONENT_TYPES.map((c) => <option key={c} value={c}>{c.replace(/_/g, " ")}</option>)}
            </select>
            <input
              value={unitsRequested}
              onChange={(e) => setUnitsRequested(e.target.value)}
              placeholder="Units requested"
              type="number"
              min={1}
              className="rounded-xl border border-border px-3 py-2 text-sm"
            />
            <input
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="Emergency reason (e.g. mass casualty)"
              className="rounded-xl border border-border px-3 py-2 text-sm"
            />
          </div>
          <button
            type="button"
            onClick={handleRequest}
            disabled={requestRedistribution.isPending || !sourceFacilityId || !targetFacilityId}
            className="rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
          >
            {requestRedistribution.isPending ? <Loader2 className="h-4 w-4 animate-spin inline" /> : null}
            Request emergency allocation
          </button>
        </section>

        <section className="rounded-2xl border border-border bg-card p-5 space-y-3">
          <h2 className="text-sm font-semibold text-foreground">Active redistribution requests</h2>
          {redistributions.isPending && <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />}
          {(redistributions.data ?? []).length === 0 && !redistributions.isPending ? (
            <p className="text-sm text-muted-foreground">No emergency redistribution requests on record.</p>
          ) : (
            <ul className="space-y-3">
              {(redistributions.data ?? []).map((row) => (
                <li key={row.redistributionId} className="rounded-xl border border-border p-3 text-sm flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <p className="font-medium">
                      {row.bloodGroup} {String(row.componentType ?? "").replace(/_/g, " ")} — {row.unitsRequested} units
                    </p>
                    <p className="text-xs text-muted-foreground mt-1">
                      {row.sourceFacilityId} → {row.targetFacilityId} · {row.status}
                      {row.handoffStatus ? ` · handoff ${row.handoffStatus}` : ""}
                      {row.reason ? ` · ${row.reason}` : ""}
                    </p>
                    {row.handoffError && (
                      <p className="text-xs text-warning-foreground mt-1">{row.handoffError}</p>
                    )}
                    {row.nhumeDeepLinkPath && (
                      <Link href={row.nhumeDeepLinkPath} className="text-xs text-rose-600 hover:underline mt-1 inline-block">
                        Track NHUME delivery →
                      </Link>
                    )}
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {row.status === "REQUESTED" && row.redistributionId && (
                      <button
                        type="button"
                        onClick={() => handleApprove(row.redistributionId!, row.unitsRequested ?? 1)}
                        disabled={approveRedistribution.isPending}
                        className="rounded-lg bg-green-600 px-3 py-1.5 text-xs text-white disabled:opacity-50"
                      >
                        Approve & fulfil
                      </button>
                    )}
                    {row.handoffStatus === "HANDOFF_FAILED" && row.redistributionId && (
                      <button
                        type="button"
                        onClick={() => handleRetryHandoff(row.redistributionId!)}
                        disabled={initiateHandoff.isPending}
                        className="rounded-lg border border-amber-300 bg-warning-soft px-3 py-1.5 text-xs text-warning-foreground disabled:opacity-50"
                      >
                        Retry NHUME handoff
                      </button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        <div className="mt-6 flex gap-3">
          <Link href="/madi/dashboard" className="text-sm text-rose-600 hover:underline">Facility dashboard</Link>
          <Link href="/madi/haemovigilance/national" className="text-sm text-rose-600 hover:underline">National haemovigilance</Link>
        </div>
      </PageShell>
    </AppLayout>
  );
}
