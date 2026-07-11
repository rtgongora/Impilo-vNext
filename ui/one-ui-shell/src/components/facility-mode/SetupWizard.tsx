"use client";

import { useState } from "react";
import Link from "next/link";
import { CheckCircle2, Circle, Loader2, Plus, Rocket, AlertTriangle, RefreshCw } from "lucide-react";
import {
  useFacilitySetupState,
  useAdvanceSetupStep,
  useFacilityUnits,
  useCreateFacilityUnit,
  useServicePoints,
  useCreateServicePoint,
  useFacilityModeContext,
} from "./useFacilityMode";
import { useFacilityQueueDefinitions } from "@/hooks/queries/useFacilityConfiguration";
import { useReconcileQueues } from "@/hooks/queries/useFacilityQueues";
import { useAssignments } from "@/hooks/useVashandi";
import { SETUP_STEPS, type FacilitySetupStepState } from "./types";

/**
 * Facility-mode setup wizard. Drives the TUSO setup-state SoR through:
 * dept -> service-point -> queue -> workflow -> workforce -> OROS-routing ->
 * Khuluma-channel -> Fundo-readiness -> go-live.
 *
 * Honest behaviour: Fundo readiness is never auto-faked (the operator confirms it);
 * go-live is rejected by TUSO if prerequisites are incomplete and the rejection is
 * surfaced verbatim.
 */
export function SetupWizard({ facilityId }: { facilityId: string }) {
  const { data: state, isLoading } = useFacilitySetupState(facilityId);
  const advance = useAdvanceSetupStep(facilityId);
  const [error, setError] = useState<string | null>(null);

  if (isLoading || !state) {
    return (
      <div className="flex items-center justify-center py-16">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  const toggle = (step: string, complete: boolean) => {
    setError(null);
    advance.mutate(
      { step, complete },
      {
        onError: (e: unknown) => {
          const msg =
            (e as { body?: { error?: { message?: string } } })?.body?.error?.message ??
            (e as Error)?.message ??
            "Setup step could not be updated.";
          setError(String(msg));
        },
      },
    );
  };

  return (
    <div className="space-y-6">
      {error && (
        <div className="flex items-start gap-2 rounded-md border border-danger/28 bg-danger-soft p-3 text-sm text-red-600">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Department configuration */}
      <DepartmentsStep facilityId={facilityId} state={state} onToggle={toggle} />

      {/* Service-point configuration */}
      <ServicePointsStep facilityId={facilityId} state={state} onToggle={toggle} />

      {/* Queue configuration — real queue-definition truth + live materialisation */}
      <QueuesStep facilityId={facilityId} state={state} onToggle={toggle} />

      {/* Workforce — real vashandi assignment truth for this facility */}
      <WorkforceStep facilityId={facilityId} state={state} onToggle={toggle} />

      {/* Remaining attestation-only steps */}
      <div className="rounded-lg border border-border bg-card p-4">
        <h3 className="mb-1 text-sm font-medium text-foreground">Operational attestations</h3>
        <p className="mb-3 text-xs text-muted-foreground">
          These steps are operator attestations — ticking them records readiness but does not
          configure the underlying system yet.
        </p>
        <ul className="space-y-2">
          {SETUP_STEPS.filter(
            (s) =>
              !["DEPARTMENTS", "SERVICE_POINTS", "QUEUES", "WORKFORCE", "GO_LIVE"].includes(s.key),
          ).map((s) => {
            const done = Boolean(state[s.flag]);
            return (
              <li key={s.key} className="flex items-center gap-3">
                <button
                  type="button"
                  disabled={advance.isPending}
                  onClick={() => toggle(s.key, !done)}
                  className="flex items-center gap-2 text-sm disabled:opacity-50"
                >
                  {done ? (
                    <CheckCircle2 className="h-5 w-5 text-emerald-500" />
                  ) : (
                    <Circle className="h-5 w-5 text-muted-foreground/50" />
                  )}
                  <span className={done ? "text-foreground" : "text-muted-foreground"}>
                    {s.label}
                  </span>
                </button>
                <span className="ml-auto text-[10px] uppercase tracking-wide text-muted-foreground/70">
                  {s.key === "FUNDO_READINESS" && !done ? "Confirm readiness" : "Attestation"}
                </span>
              </li>
            );
          })}
        </ul>
      </div>

      {/* Go-live */}
      <GoLiveStep state={state} onToggle={toggle} pending={advance.isPending} />
    </div>
  );
}

function StepCard({
  title,
  flag,
  state,
  stepKey,
  onToggle,
  children,
}: {
  title: string;
  flag: keyof FacilitySetupStepState;
  state: FacilitySetupStepState;
  stepKey: string;
  onToggle: (step: string, complete: boolean) => void;
  children: React.ReactNode;
}) {
  const done = Boolean(state[flag]);
  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <div className="mb-3 flex items-center justify-between">
        <h3 className="flex items-center gap-2 text-sm font-medium text-foreground">
          {done ? (
            <CheckCircle2 className="h-5 w-5 text-emerald-500" />
          ) : (
            <Circle className="h-5 w-5 text-muted-foreground/50" />
          )}
          {title}
        </h3>
        <button
          type="button"
          onClick={() => onToggle(stepKey, !done)}
          className="text-xs font-medium text-primary hover:underline"
        >
          {done ? "Mark incomplete" : "Mark complete"}
        </button>
      </div>
      {children}
    </div>
  );
}

function DepartmentsStep({
  facilityId,
  state,
  onToggle,
}: {
  facilityId: string;
  state: FacilitySetupStepState;
  onToggle: (step: string, complete: boolean) => void;
}) {
  const { data: units } = useFacilityUnits(facilityId);
  const create = useCreateFacilityUnit(facilityId);
  const [name, setName] = useState("");
  const [serviceLine, setServiceLine] = useState("");

  return (
    <StepCard
      title="Departments"
      flag="departmentsConfigured"
      stepKey="DEPARTMENTS"
      state={state}
      onToggle={onToggle}
    >
      <ul className="mb-3 space-y-1">
        {(units ?? []).map((u) => (
          <li key={u.id} className="flex items-center justify-between text-sm">
            <span className="text-foreground">{u.name}</span>
            <span className="text-xs text-muted-foreground">
              {u.serviceLine ?? u.unitType}
            </span>
          </li>
        ))}
        {(units ?? []).length === 0 && (
          <li className="text-xs text-muted-foreground">No departments yet.</li>
        )}
      </ul>
      <div className="flex flex-wrap gap-2">
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Department name"
          className="flex-1 rounded-md border border-border bg-background px-2 py-1.5 text-sm"
        />
        <input
          value={serviceLine}
          onChange={(e) => setServiceLine(e.target.value)}
          placeholder="Service line"
          className="w-32 rounded-md border border-border bg-background px-2 py-1.5 text-sm"
        />
        <button
          type="button"
          disabled={!name.trim() || create.isPending}
          onClick={() =>
            create.mutate(
              { name: name.trim(), serviceLine: serviceLine.trim() || undefined },
              { onSuccess: () => { setName(""); setServiceLine(""); } },
            )
          }
          className="inline-flex items-center gap-1 rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground disabled:opacity-50"
        >
          <Plus className="h-4 w-4" /> Add
        </button>
      </div>
    </StepCard>
  );
}

function ServicePointsStep({
  facilityId,
  state,
  onToggle,
}: {
  facilityId: string;
  state: FacilitySetupStepState;
  onToggle: (step: string, complete: boolean) => void;
}) {
  const { data: sps } = useServicePoints(facilityId);
  const { data: units } = useFacilityUnits(facilityId);
  const create = useCreateServicePoint(facilityId);
  const [name, setName] = useState("");
  const [unitId, setUnitId] = useState<string>("");

  return (
    <StepCard
      title="Service points"
      flag="servicePointsConfigured"
      stepKey="SERVICE_POINTS"
      state={state}
      onToggle={onToggle}
    >
      <ul className="mb-3 space-y-1">
        {(sps ?? []).map((sp) => (
          <li key={sp.id} className="flex items-center justify-between text-sm">
            <span className="text-foreground">{sp.name}</span>
            <span className="text-xs text-muted-foreground">{sp.servicePointType}</span>
          </li>
        ))}
        {(sps ?? []).length === 0 && (
          <li className="text-xs text-muted-foreground">No service points yet.</li>
        )}
      </ul>
      <div className="flex flex-wrap gap-2">
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Service point name"
          className="flex-1 rounded-md border border-border bg-background px-2 py-1.5 text-sm"
        />
        <select
          value={unitId}
          onChange={(e) => setUnitId(e.target.value)}
          className="w-36 rounded-md border border-border bg-background px-2 py-1.5 text-sm"
        >
          <option value="">No department</option>
          {(units ?? []).map((u) => (
            <option key={u.id} value={u.id}>
              {u.name}
            </option>
          ))}
        </select>
        <button
          type="button"
          disabled={!name.trim() || create.isPending}
          onClick={() =>
            create.mutate(
              {
                name: name.trim(),
                facilityUnitId: unitId ? Number(unitId) : undefined,
              },
              { onSuccess: () => setName("") },
            )
          }
          className="inline-flex items-center gap-1 rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground disabled:opacity-50"
        >
          <Plus className="h-4 w-4" /> Add
        </button>
      </div>
    </StepCard>
  );
}

function QueuesStep({
  facilityId,
  state,
  onToggle,
}: {
  facilityId: string;
  state: FacilitySetupStepState;
  onToggle: (step: string, complete: boolean) => void;
}) {
  const defsQuery = useFacilityQueueDefinitions(String(facilityId));
  const defs = defsQuery.data?.data ?? [];
  const { data: contextEnvelope } = useFacilityModeContext(facilityId);
  const facilityUuid = contextEnvelope?.context?.facility?.facilityUuid ?? undefined;
  const reconcile = useReconcileQueues(facilityUuid);
  const [reconcileNote, setReconcileNote] = useState<string | null>(null);

  return (
    <StepCard
      title="Queues"
      flag="queuesConfigured"
      stepKey="QUEUES"
      state={state}
      onToggle={onToggle}
    >
      <p className="mb-2 text-xs text-muted-foreground">
        Queues are derived from this facility&apos;s service points and materialised live in the
        patient-flow engine.
      </p>
      <ul className="mb-3 space-y-1">
        {defs.map((d) => (
          <li key={d.sourceRef} className="flex items-center justify-between text-sm">
            <span className="text-foreground">{d.displayName}</span>
            <span className="text-xs text-muted-foreground">
              {d.queueType ?? "QUEUE"}
              {d.active === false ? " · inactive" : ""}
            </span>
          </li>
        ))}
        {defs.length === 0 && (
          <li className="text-xs text-muted-foreground">
            No queue definitions yet — add service points first; each active service point
            defines a queue.
          </li>
        )}
      </ul>
      <div className="flex items-center gap-3">
        <button
          type="button"
          disabled={!facilityUuid || reconcile.isPending}
          onClick={() => {
            setReconcileNote(null);
            reconcile.mutate(undefined, {
              onSuccess: (resp) => {
                const r = ((resp as unknown as { data?: Record<string, unknown> })?.data ?? {}) as Record<string, unknown>;
                setReconcileNote(
                  `Materialised: created ${r.created ?? 0}, updated ${r.updated ?? 0}, retired ${r.retired ?? 0}.`,
                );
              },
              onError: (e: unknown) =>
                setReconcileNote(
                  (e as Error)?.message ?? "Queue materialisation failed — see facility ops.",
                ),
            });
          }}
          className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm font-medium text-foreground disabled:opacity-50"
        >
          <RefreshCw className={`h-4 w-4 ${reconcile.isPending ? "animate-spin" : ""}`} />
          Materialise live queues
        </button>
        {reconcileNote && <span className="text-xs text-muted-foreground">{reconcileNote}</span>}
      </div>
    </StepCard>
  );
}

function WorkforceStep({
  facilityId,
  state,
  onToggle,
}: {
  facilityId: string;
  state: FacilitySetupStepState;
  onToggle: (step: string, complete: boolean) => void;
}) {
  const { data: contextEnvelope } = useFacilityModeContext(facilityId);
  const facilityUuid = contextEnvelope?.context?.facility?.facilityUuid ?? undefined;
  const { data: assignmentsResp, isLoading } = useAssignments(
    facilityUuid ? { facilityId: facilityUuid } : undefined,
  );
  // Guard against an unscoped fetch while the facility UUID is still loading.
  const assignments = (assignmentsResp?.items ?? []).filter(
    (a) => !facilityUuid || !a.facilityId || a.facilityId === facilityUuid,
  );

  return (
    <StepCard
      title="Workforce"
      flag="workforceLinked"
      stepKey="WORKFORCE"
      state={state}
      onToggle={onToggle}
    >
      <p className="mb-2 text-xs text-muted-foreground">
        Staff work here through governed Vashandi assignments bound to this facility.
      </p>
      <ul className="mb-3 space-y-1">
        {assignments.slice(0, 8).map((a) => (
          <li key={a.id} className="flex items-center justify-between text-sm">
            <span className="text-foreground">
              {a.roleTemplateId ?? a.assignmentType ?? "Assignment"}
            </span>
            <span className="text-xs text-muted-foreground">{a.status ?? "—"}</span>
          </li>
        ))}
        {!isLoading && assignments.length === 0 && (
          <li className="text-xs text-muted-foreground">
            No workforce assignments are bound to this facility yet.
          </li>
        )}
      </ul>
      <Link
        href="/work/vashandi/assignments"
        className="text-xs font-medium text-primary hover:underline"
      >
        Manage assignments in Vashandi →
      </Link>
    </StepCard>
  );
}

function GoLiveStep({
  state,
  onToggle,
  pending,
}: {
  state: FacilitySetupStepState;
  onToggle: (step: string, complete: boolean) => void;
  pending: boolean;
}) {
  return (
    <div
      className={`rounded-lg border p-4 ${
        state.goLive
          ? "border-emerald-500/30 bg-emerald-500/10"
          : "border-border bg-card"
      }`}
    >
      <div className="flex items-center justify-between">
        <div>
          <h3 className="flex items-center gap-2 text-sm font-medium text-foreground">
            <Rocket className="h-4 w-4" /> Go live
          </h3>
          <p className="mt-1 text-xs text-muted-foreground">
            {state.goLive
              ? "Facility is live."
              : state.readyForGoLive
                ? "All prerequisites complete. Ready to go live."
                : `Next required step: ${state.nextStep ?? "—"}`}
          </p>
        </div>
        <button
          type="button"
          disabled={pending || (!state.goLive && !state.readyForGoLive)}
          onClick={() => onToggle("GO_LIVE", !state.goLive)}
          className="rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
        >
          {state.goLive ? "Take offline" : "Go live"}
        </button>
      </div>
    </div>
  );
}
