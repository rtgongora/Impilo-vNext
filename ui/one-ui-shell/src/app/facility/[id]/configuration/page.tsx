"use client";

/**
 * TUSO Facility Configuration Console — service points, workspaces, derived queue definitions and
 * readiness. TUSO is the source of truth for facility configuration; queue definitions are derived
 * from service points and materialised into PCT for care operations (not authored here). Missing
 * sections are reported honestly and never marked complete.
 *
 * Route: /facility/[id]/configuration
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { AlertTriangle, ArrowLeft, CheckCircle2, Circle, Clock, Loader2, MinusCircle, RefreshCw } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import {
  useFacilityConfiguration,
  useFacilitySetupChecklist,
  useConfigServicePoints,
  useConfigWorkspaces,
  useFacilityQueueDefinitions,
  useFacilityDownstreamReadiness,
  useFacilityConfigHistory,
  useCreateServicePoint,
  useUpdateServicePoint,
  useRetireServicePoint,
  useCreateWorkspace,
  useUpdateWorkspace,
  useRetireWorkspace,
  usePublishConfiguration,
  type ChecklistItem,
  type ConfigServicePoint,
  type ConfigWorkspace,
} from "@/hooks/queries/useFacilityConfiguration";
import {
  useQueueMaterializationStatus,
  useFacilityQueuesAdmin,
  useReconcileQueues,
} from "@/hooks/queries/useFacilityQueues";

type TabKey = "overview" | "service-points" | "workspaces" | "queues" | "readiness" | "audit";

const TABS: { key: TabKey; label: string }[] = [
  { key: "overview", label: "Setup overview" },
  { key: "service-points", label: "Service points" },
  { key: "workspaces", label: "Workspaces" },
  { key: "queues", label: "Queue definitions" },
  { key: "readiness", label: "Readiness checklist" },
  { key: "audit", label: "Audit / history" },
];

const SERVICE_POINT_TYPES = ["CONSULTATION", "TRIAGE", "DISPENSING", "REGISTRATION", "LABORATORY", "GENERAL"];
const WORKSPACE_TYPES = ["OPD", "LAB", "PHARMACY", "WARD", "TRIAGE", "ADMIN"];

function fmt(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "—" : d.toLocaleString();
}

function ChecklistStatusIcon({ status }: { status: string }) {
  if (status === "COMPLETE") return <CheckCircle2 className="h-4 w-4 text-success" />;
  if (status === "MISSING") return <AlertTriangle className="h-4 w-4 text-danger" />;
  if (status === "NOT_APPLICABLE") return <MinusCircle className="h-4 w-4 text-muted-foreground" />;
  return <Circle className="h-4 w-4 text-warning-foreground" />;
}

export default function FacilityConfigurationPage() {
  const params = useParams();
  const facilityId = params.id as string;
  const [tab, setTab] = useState<TabKey>("overview");

  const configQuery = useFacilityConfiguration(facilityId);
  const summary = configQuery.data?.data;

  return (
    <AppLayout>
      <PageShell
        title="Facility configuration"
        subtitle="Service points, workspaces, queues and readiness (TUSO source of truth)"
      >
        <div className="mb-4 flex items-center justify-between gap-4 flex-wrap">
          <Link
            href={`/facility/${facilityId}/cockpit`}
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Back to cockpit
          </Link>
          <FeatureMaturityBadge status="connected" detail="TUSO facility configuration via BFF admin routes" />
        </div>

        <p className="mb-4 text-xs text-muted-foreground">
          TUSO owns facility configuration. Queue definitions are derived from service points and
          materialised into PCT for care operations — they are not authored here. Missing sections are
          shown honestly and are never marked complete.
        </p>

        {/* Tabs */}
        <div className="mb-4 flex flex-wrap gap-1 border-b border-border">
          {TABS.map((t) => (
            <button
              key={t.key}
              onClick={() => setTab(t.key)}
              className={`px-3 py-2 text-sm font-medium border-b-2 -mb-px ${
                tab === t.key
                  ? "border-primary text-foreground"
                  : "border-transparent text-muted-foreground hover:text-foreground"
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>

        {configQuery.isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
        ) : configQuery.isError || !summary ? (
          <div className="bg-card rounded-lg border border-border p-12 text-center">
            <p className="text-sm text-muted-foreground">
              Unable to load facility configuration. TUSO may be unreachable, or the facility does not exist.
            </p>
          </div>
        ) : (
          <>
            {tab === "overview" && <OverviewTab facilityId={facilityId} />}
            {tab === "service-points" && <ServicePointsTab facilityId={facilityId} />}
            {tab === "workspaces" && <WorkspacesTab facilityId={facilityId} />}
            {tab === "queues" && <QueuesTab facilityId={facilityId} facilityUuid={summary.facilityUuid} />}
            {tab === "readiness" && <ReadinessTab facilityId={facilityId} />}
            {tab === "audit" && <AuditTab facilityId={facilityId} />}
          </>
        )}
      </PageShell>
    </AppLayout>
  );
}

function StateBadge({ state }: { state: string }) {
  const style =
    state === "CONFIGURED" || state === "MATERIALISED"
      ? "bg-green-100 text-green-700"
      : state === "IMPORTED_PENDING_CONFIGURATION" || state === "PARTIALLY_CONFIGURED" || state === "NEEDS_ATTENTION"
        ? "bg-amber-100 text-warning-foreground"
        : state === "FAILED_MATERIALISATION"
          ? "bg-red-100 text-danger"
          : "bg-neutral-100 text-muted-foreground";
  return <span className={`inline-block px-2 py-1 text-xs rounded-full font-medium ${style}`}>{state}</span>;
}

function OverviewTab({ facilityId }: { facilityId: string }) {
  const { data } = useFacilityConfiguration(facilityId);
  const publish = usePublishConfiguration(facilityId);
  const [msg, setMsg] = useState<string | null>(null);
  const s = data?.data;
  if (!s) return null;

  const rows: [string, string][] = [
    ["Name", s.name],
    ["Internal ID", String(s.facilityId)],
    ["Facility UUID", s.facilityUuid ?? "—"],
    ["Facility code", s.facilityCode ?? "—"],
    ["Facility type", s.facilityType ?? "—"],
    ["Province / district", `${s.province ?? "—"} / ${s.district ?? "—"}`],
    ["Regulatory status", s.regulatoryStatus ?? "—"],
    ["Operational status", s.operationalStatus ?? "—"],
    ["Source provenance", s.sourceProvenance ?? "—"],
    ["Config version", s.configVersion != null ? String(s.configVersion) : "—"],
    ["Last config change", fmt(s.lastConfigChangeAt)],
  ];

  return (
    <div className="space-y-4">
      <div className="bg-card rounded-lg border border-border p-5">
        <div className="flex items-center justify-between gap-4 flex-wrap">
          <div className="flex items-center gap-3">
            <StateBadge state={s.configurationState} />
            <span className="text-sm text-muted-foreground">
              {s.setupComplete ? "Required configuration complete" : "Required configuration incomplete"}
            </span>
          </div>
          <div className="flex items-center gap-2">
            {msg && <span className="text-xs text-muted-foreground">{msg}</span>}
            <button
              className="inline-flex items-center gap-1 px-3 py-1.5 text-sm rounded border border-border hover:bg-background disabled:opacity-40"
              disabled={publish.isPending}
              onClick={() => {
                setMsg(null);
                publish
                  .mutateAsync()
                  .then(() => setMsg("Configuration update published to downstream"))
                  .catch(() => setMsg("Publish failed (TUSO may be unreachable)"));
              }}
            >
              <RefreshCw className={`h-4 w-4 ${publish.isPending ? "animate-spin" : ""}`} />
              {publish.isPending ? "Publishing…" : "Publish configuration update"}
            </button>
          </div>
        </div>
        <div className="mt-4 grid grid-cols-3 gap-3 text-center">
          <Metric label="Service points" value={s.activeServicePointCount} />
          <Metric label="Workspaces" value={s.activeWorkspaceCount} />
          <Metric label="Queue definitions" value={s.activeQueueDefinitionCount} />
        </div>
      </div>

      {s.missingSections.length > 0 && (
        <div className="bg-amber-50 border border-amber-200 rounded-lg p-4">
          <p className="text-sm font-medium text-warning-foreground">Missing required sections</p>
          <ul className="mt-2 list-disc list-inside text-sm text-warning-foreground">
            {s.missingSections.map((m) => (
              <li key={m}>{m.replace(/_/g, " ")}</li>
            ))}
          </ul>
        </div>
      )}

      <div className="bg-card rounded-lg border border-border overflow-hidden">
        <table className="w-full text-sm">
          <tbody>
            {rows.map(([label, value]) => (
              <tr key={label} className="border-b border-border last:border-0">
                <td className="px-4 py-2 text-muted-foreground w-1/3">{label}</td>
                <td className="px-4 py-2 text-foreground break-all">{value}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg border border-border p-3">
      <p className="text-2xl font-semibold text-foreground">{value}</p>
      <p className="text-xs text-muted-foreground">{label}</p>
    </div>
  );
}

function ServicePointsTab({ facilityId }: { facilityId: string }) {
  const { data, isLoading } = useConfigServicePoints(facilityId);
  const create = useCreateServicePoint(facilityId);
  const update = useUpdateServicePoint(facilityId);
  const retire = useRetireServicePoint(facilityId);
  const [name, setName] = useState("");
  const [type, setType] = useState(SERVICE_POINT_TYPES[0]);
  const [editing, setEditing] = useState<string | null>(null);
  const [editName, setEditName] = useState("");
  const items = data?.data ?? [];

  return (
    <div className="space-y-4">
      <form
        className="bg-card rounded-lg border border-border p-4 flex flex-wrap items-end gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          if (!name.trim()) return;
          create.mutateAsync({ name: name.trim(), servicePointType: type }).then(() => setName(""));
        }}
      >
        <label className="flex flex-col text-xs text-muted-foreground">
          Name
          <input
            className="mt-1 border border-border rounded px-2 py-1 text-sm text-foreground"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Triage Desk"
          />
        </label>
        <label className="flex flex-col text-xs text-muted-foreground">
          Type
          <select
            className="mt-1 border border-border rounded px-2 py-1 text-sm text-foreground"
            value={type}
            onChange={(e) => setType(e.target.value)}
          >
            {SERVICE_POINT_TYPES.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </label>
        <button
          type="submit"
          disabled={create.isPending || !name.trim()}
          className="px-3 py-1.5 text-sm rounded bg-primary text-primary-foreground disabled:opacity-40"
        >
          {create.isPending ? "Adding…" : "Add service point"}
        </button>
      </form>

      {isLoading ? (
        <Loading />
      ) : items.length === 0 ? (
        <Empty message="No service points configured yet." />
      ) : (
        <div className="space-y-2">
          {items.map((sp: ConfigServicePoint) => (
            <div key={sp.id} className="bg-card rounded-lg border border-border p-4">
              <div className="flex items-center justify-between gap-3 flex-wrap">
                <div>
                  {editing === sp.id ? (
                    <input
                      className="border border-border rounded px-2 py-1 text-sm text-foreground"
                      value={editName}
                      onChange={(e) => setEditName(e.target.value)}
                    />
                  ) : (
                    <p className="text-sm font-medium text-foreground">
                      {sp.name}
                      {!sp.active && <span className="ml-2 text-xs text-muted-foreground">(retired)</span>}
                    </p>
                  )}
                  <p className="mt-1 text-xs text-muted-foreground">
                    type: {sp.servicePointType} · status: {sp.status}
                    {sp.code ? ` · code: ${sp.code}` : ""}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {editing === sp.id ? (
                    <>
                      <button
                        className="px-2 py-1 text-xs rounded border border-border hover:bg-background"
                        disabled={update.isPending}
                        onClick={() =>
                          update
                            .mutateAsync({ id: sp.id, input: { name: editName } })
                            .then(() => setEditing(null))
                        }
                      >
                        Save
                      </button>
                      <button
                        className="px-2 py-1 text-xs rounded border border-border hover:bg-background"
                        onClick={() => setEditing(null)}
                      >
                        Cancel
                      </button>
                    </>
                  ) : (
                    <>
                      <button
                        className="px-2 py-1 text-xs rounded border border-border hover:bg-background"
                        onClick={() => {
                          setEditing(sp.id);
                          setEditName(sp.name);
                        }}
                      >
                        Edit
                      </button>
                      {sp.active && (
                        <button
                          className="px-2 py-1 text-xs rounded border border-danger/30 text-danger hover:bg-danger-soft disabled:opacity-40"
                          disabled={retire.isPending}
                          onClick={() => retire.mutate(sp.id)}
                        >
                          Retire
                        </button>
                      )}
                    </>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function WorkspacesTab({ facilityId }: { facilityId: string }) {
  const { data, isLoading } = useConfigWorkspaces(facilityId);
  const create = useCreateWorkspace(facilityId);
  const update = useUpdateWorkspace(facilityId);
  const retire = useRetireWorkspace(facilityId);
  const [name, setName] = useState("");
  const [type, setType] = useState(WORKSPACE_TYPES[0]);
  const [editing, setEditing] = useState<string | null>(null);
  const [editName, setEditName] = useState("");
  const items = data?.data ?? [];

  return (
    <div className="space-y-4">
      <form
        className="bg-card rounded-lg border border-border p-4 flex flex-wrap items-end gap-3"
        onSubmit={(e) => {
          e.preventDefault();
          if (!name.trim()) return;
          create.mutateAsync({ name: name.trim(), workspaceType: type }).then(() => setName(""));
        }}
      >
        <label className="flex flex-col text-xs text-muted-foreground">
          Name
          <input
            className="mt-1 border border-border rounded px-2 py-1 text-sm text-foreground"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. OPD Consulting"
          />
        </label>
        <label className="flex flex-col text-xs text-muted-foreground">
          Type
          <select
            className="mt-1 border border-border rounded px-2 py-1 text-sm text-foreground"
            value={type}
            onChange={(e) => setType(e.target.value)}
          >
            {WORKSPACE_TYPES.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </label>
        <button
          type="submit"
          disabled={create.isPending || !name.trim()}
          className="px-3 py-1.5 text-sm rounded bg-primary text-primary-foreground disabled:opacity-40"
        >
          {create.isPending ? "Adding…" : "Add workspace"}
        </button>
      </form>

      {isLoading ? (
        <Loading />
      ) : items.length === 0 ? (
        <Empty message="No workspaces configured yet." />
      ) : (
        <div className="space-y-2">
          {items.map((ws: ConfigWorkspace) => (
            <div key={ws.id} className="bg-card rounded-lg border border-border p-4">
              <div className="flex items-center justify-between gap-3 flex-wrap">
                <div>
                  {editing === ws.id ? (
                    <input
                      className="border border-border rounded px-2 py-1 text-sm text-foreground"
                      value={editName}
                      onChange={(e) => setEditName(e.target.value)}
                    />
                  ) : (
                    <p className="text-sm font-medium text-foreground">
                      {ws.name}
                      {!ws.active && <span className="ml-2 text-xs text-muted-foreground">(retired)</span>}
                    </p>
                  )}
                  <p className="mt-1 text-xs text-muted-foreground">type: {ws.workspaceType}</p>
                </div>
                <div className="flex items-center gap-2">
                  {editing === ws.id ? (
                    <>
                      <button
                        className="px-2 py-1 text-xs rounded border border-border hover:bg-background"
                        disabled={update.isPending}
                        onClick={() =>
                          update.mutateAsync({ id: ws.id, input: { name: editName } }).then(() => setEditing(null))
                        }
                      >
                        Save
                      </button>
                      <button
                        className="px-2 py-1 text-xs rounded border border-border hover:bg-background"
                        onClick={() => setEditing(null)}
                      >
                        Cancel
                      </button>
                    </>
                  ) : (
                    <>
                      <button
                        className="px-2 py-1 text-xs rounded border border-border hover:bg-background"
                        onClick={() => {
                          setEditing(ws.id);
                          setEditName(ws.name);
                        }}
                      >
                        Edit
                      </button>
                      {ws.active && (
                        <button
                          className="px-2 py-1 text-xs rounded border border-danger/30 text-danger hover:bg-danger-soft disabled:opacity-40"
                          disabled={retire.isPending}
                          onClick={() => retire.mutate(ws.id)}
                        >
                          Retire
                        </button>
                      )}
                    </>
                  )}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function QueuesTab({ facilityId, facilityUuid }: { facilityId: string; facilityUuid: string | null }) {
  const defsQuery = useFacilityQueueDefinitions(facilityId);
  const status = useQueueMaterializationStatus(facilityUuid ?? undefined);
  const queuesQuery = useFacilityQueuesAdmin(facilityUuid ?? undefined);
  const reconcile = useReconcileQueues(facilityUuid ?? undefined);
  const [msg, setMsg] = useState<string | null>(null);

  const defs = defsQuery.data?.data ?? [];
  const summary = status.data?.data;
  const pctQueues = queuesQuery.data?.data ?? [];

  return (
    <div className="space-y-4">
      <p className="text-xs text-muted-foreground">
        These queue definitions are owned by TUSO and derived from active service points. They are read-only
        here; PCT materialises them for care operations. Use “Reconcile from TUSO” to re-materialise.
      </p>

      <div className="bg-card rounded-lg border border-border p-4 flex items-center justify-between gap-4 flex-wrap">
        <div className="text-sm text-muted-foreground">
          {summary ? (
            <span>
              PCT: {summary.materialisedFromTuso} materialised from TUSO · {summary.seedDemoOnly} seed/demo ·
              last materialised {fmt(summary.lastMaterializedAt)}
            </span>
          ) : status.isLoading ? (
            <Loader2 className="h-4 w-4 animate-spin" />
          ) : (
            <span>Live PCT materialisation status unavailable</span>
          )}
        </div>
        <div className="flex items-center gap-2">
          {msg && <span className="text-xs text-muted-foreground">{msg}</span>}
          <button
            className="inline-flex items-center gap-1 px-3 py-1.5 text-sm rounded border border-border hover:bg-background disabled:opacity-40"
            disabled={reconcile.isPending || !facilityUuid}
            onClick={() => {
              setMsg(null);
              reconcile
                .mutateAsync()
                .then((r) => {
                  const d = r.data;
                  setMsg(d ? `Reconciled: ${d.created} created, ${d.updated} updated, ${d.retired} retired` : "Reconciled");
                })
                .catch(() => setMsg("Reconcile failed (TUSO/PCT may be unreachable)"));
            }}
          >
            <RefreshCw className={`h-4 w-4 ${reconcile.isPending ? "animate-spin" : ""}`} />
            {reconcile.isPending ? "Reconciling…" : "Reconcile from TUSO"}
          </button>
        </div>
      </div>

      {defsQuery.isLoading ? (
        <Loading />
      ) : defs.length === 0 ? (
        <Empty message="No queue definitions — configure active service points to derive queues." />
      ) : (
        <div className="space-y-2">
          {defs.map((q) => {
            const pct = pctQueues.find((p) => p.sourceRef === q.sourceRef);
            const isSeed = pct?.source === "SEED_DEMO";
            return (
              <div key={q.sourceRef} className="bg-card rounded-lg border border-border p-4">
                <p className="text-sm font-medium text-foreground">
                  {q.displayName}
                  {!q.active && <span className="ml-2 text-xs text-muted-foreground">(inactive)</span>}
                </p>
                <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                  <span>type: {q.queueType}</span>
                  {q.priorityFlag && <span className="text-warning-foreground">priority/triage</span>}
                  {q.walkInSupported && <span>walk-in</span>}
                  {q.appointmentSupported && <span>appointment</span>}
                  <span>ref: {q.sourceRef}</span>
                  {pct && <span>PCT: {pct.materializationStatus}</span>}
                </div>
                {isSeed && (
                  <p className="mt-2 text-xs text-warning-foreground">
                    Seed/demo queue — not materialised from TUSO. Not production facility truth.
                  </p>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function ReadinessTab({ facilityId }: { facilityId: string }) {
  const { data, isLoading } = useFacilitySetupChecklist(facilityId);
  const downstream = useFacilityDownstreamReadiness(facilityId);
  const checklist = data?.data;

  if (isLoading) return <Loading />;
  if (!checklist) return <Empty message="Readiness checklist unavailable." />;

  return (
    <div className="space-y-4">
      <div className="bg-card rounded-lg border border-border overflow-hidden">
        <ul>
          {checklist.items.map((item: ChecklistItem) => (
            <li key={item.key} className="flex items-start gap-3 border-b border-border px-4 py-3 last:border-0">
              <ChecklistStatusIcon status={item.status} />
              <div className="flex-1">
                <p className="text-sm text-foreground">
                  {item.label}
                  {item.required && <span className="ml-2 text-[10px] uppercase text-muted-foreground">required</span>}
                </p>
                {item.detail && <p className="text-xs text-muted-foreground">{item.detail}</p>}
              </div>
              <span className="text-xs font-medium text-muted-foreground">{item.status}</span>
            </li>
          ))}
        </ul>
      </div>
      {downstream.data?.data && (
        <div className="bg-card rounded-lg border border-border p-4 text-xs text-muted-foreground">
          {downstream.data.data.note}
        </div>
      )}
    </div>
  );
}

function AuditTab({ facilityId }: { facilityId: string }) {
  const { data, isLoading } = useFacilityConfigHistory(facilityId);
  const events = data?.data ?? [];
  if (isLoading) return <Loading />;
  if (events.length === 0)
    return <Empty message="No configuration change events recorded yet for this facility." />;
  return (
    <div className="bg-card rounded-lg border border-border overflow-hidden">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border text-left text-xs text-muted-foreground">
            <th className="px-4 py-2">Event</th>
            <th className="px-4 py-2">Aggregate</th>
            <th className="px-4 py-2">Occurred</th>
            <th className="px-4 py-2">Published</th>
          </tr>
        </thead>
        <tbody>
          {events.map((e, i) => (
            <tr key={`${e.aggregateId}-${i}`} className="border-b border-border last:border-0">
              <td className="px-4 py-2 text-foreground">{e.eventType}</td>
              <td className="px-4 py-2 text-muted-foreground">{e.aggregateType}</td>
              <td className="px-4 py-2 text-muted-foreground">{fmt(e.occurredAt)}</td>
              <td className="px-4 py-2 text-muted-foreground">
                <span className="inline-flex items-center gap-1">
                  <Clock className="h-3 w-3" /> {fmt(e.publishedAt)}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Loading() {
  return (
    <div className="flex items-center justify-center py-16">
      <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
    </div>
  );
}

function Empty({ message }: { message: string }) {
  return (
    <div className="bg-card rounded-lg border border-border p-12 text-center">
      <p className="text-sm text-muted-foreground">{message}</p>
    </div>
  );
}
