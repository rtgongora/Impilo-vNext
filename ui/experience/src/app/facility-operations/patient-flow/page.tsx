"use client";

/**
 * Patient Flow Board — facility-scoped movement across queue states (live BFF data).
 * Route: /facility-operations/patient-flow
 */

import Link from "next/link";
import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Activity, ArrowLeft } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useQueueEntries, type QueueEntryResource } from "@/hooks/queries/useQueue";
import { useFacilityQueueStats } from "@/hooks/queries/useFacilityOperations";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import {
  getQueuePatientId,
  getQueuePatientName,
  formatQueueWaitTime,
  getQueueQueuedAt,
} from "@/lib/queue-workflows";

type AppointmentLite = {
  id: string;
  attributes: { patient_id: string; status: string; scheduled_at: string; appointment_type: string };
};

function FlowColumn({
  title,
  tone,
  entries,
  empty,
}: {
  title: string;
  tone: string;
  entries: QueueEntryResource[];
  empty: string;
}) {
  return (
    <div className={`rounded-2xl border bg-white ${tone}`}>
      <div className="border-b border-slate-100 px-4 py-3">
        <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-600">{title}</h3>
        <p className="text-[11px] text-slate-400">{entries.length} entr{entries.length === 1 ? "y" : "ies"}</p>
      </div>
      <div className="max-h-[min(60vh,520px)] space-y-2 overflow-y-auto p-3">
        {entries.length === 0 ? (
          <p className="px-1 py-6 text-center text-xs text-slate-500">{empty}</p>
        ) : (
          entries.map((e) => {
            const pid = getQueuePatientId(e);
            const label = getQueuePatientName(e);
            const at = getQueueQueuedAt(e);
            const wait = at ? formatQueueWaitTime(at) : "—";
            const st = String(e.attributes.status ?? "");
            return (
              <div key={e.id} className="rounded-xl border border-slate-100 bg-slate-50/80 px-3 py-2">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-xs font-semibold text-slate-900">{label}</span>
                  <span className="text-[10px] font-mono text-slate-500">{st}</span>
                </div>
                <p className="mt-1 text-[11px] text-slate-600">Waiting {wait}</p>
                {pid ? (
                  <Link
                    href={`/ehr/${pid}`}
                    className="mt-2 inline-block text-[11px] font-semibold text-impilo-700 underline"
                  >
                    Open chart
                  </Link>
                ) : (
                  <p className="mt-2 text-[10px] text-slate-500">Patient id not available on this entry.</p>
                )}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
}

export default function PatientFlowBoardPage() {
  const facility = useFacilityStore((s) => s.facility);
  const fid = facility?.id;

  const waiting = useQueueEntries(fid ? { facilityId: fid, status: "WAITING" } : undefined);
  const called = useQueueEntries(fid ? { facilityId: fid, status: "CALLED" } : undefined);
  const inProgress = useQueueEntries(fid ? { facilityId: fid, status: "IN_PROGRESS" } : undefined);
  /** PCT queue-type slices — unified “journey lanes” until a single journey/timeline board API exists. */
  const laneTriage = useQueueEntries(fid ? { facilityId: fid, status: "WAITING", queueType: "TRIAGE" } : undefined);
  const laneConsult = useQueueEntries(
    fid ? { facilityId: fid, status: "WAITING", queueType: "CONSULTATION" } : undefined,
  );
  const laneLab = useQueueEntries(fid ? { facilityId: fid, status: "WAITING", queueType: "LABORATORY" } : undefined);
  const lanePharm = useQueueEntries(fid ? { facilityId: fid, status: "WAITING", queueType: "PHARMACY" } : undefined);
  const statsQ = useFacilityQueueStats(fid);

  const appointmentsTodayQ = useQuery({
    queryKey: ["appointments-today-count", fid],
    enabled: !!fid,
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<AppointmentLite[]>>(
        `/internal/v1/appointments?facility_id=${encodeURIComponent(fid!)}`,
      );
      const today = new Date().toISOString().split("T")[0];
      const items = res.data ?? [];
      return items.filter((a) => a.attributes.scheduled_at?.startsWith(today)).length;
    },
  });

  const w = waiting.data?.data ?? [];
  const c = called.data?.data ?? [];
  const p = inProgress.data?.data ?? [];
  const stats = statsQ.data?.data;

  const loading =
    !!fid &&
    (waiting.isLoading ||
      called.isLoading ||
      inProgress.isLoading ||
      laneTriage.isLoading ||
      laneConsult.isLoading ||
      laneLab.isLoading ||
      lanePharm.isLoading ||
      statsQ.isLoading ||
      appointmentsTodayQ.isLoading);

  const summary = useMemo(() => {
    if (!stats) return null;
    return (
      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <div className="rounded-xl border border-slate-200 bg-white p-3">
          <dt className="text-[10px] font-semibold uppercase text-slate-500">Waiting (stats)</dt>
          <dd className="text-lg font-bold text-slate-900">{stats.waiting}</dd>
        </div>
        <div className="rounded-xl border border-slate-200 bg-white p-3">
          <dt className="text-[10px] font-semibold uppercase text-slate-500">Called</dt>
          <dd className="text-lg font-bold text-slate-900">{stats.called}</dd>
        </div>
        <div className="rounded-xl border border-slate-200 bg-white p-3">
          <dt className="text-[10px] font-semibold uppercase text-slate-500">In service</dt>
          <dd className="text-lg font-bold text-slate-900">{stats.inService}</dd>
        </div>
        <div className="rounded-xl border border-slate-200 bg-white p-3">
          <dt className="text-[10px] font-semibold uppercase text-slate-500">Appts today</dt>
          <dd className="text-lg font-bold text-slate-900">
            {appointmentsTodayQ.isLoading ? "…" : (appointmentsTodayQ.data ?? "—")}
          </dd>
        </div>
      </dl>
    );
  }, [appointmentsTodayQ.data, stats]);

  return (
    <AppLayout>
      <PageShell
        title="Patient flow board"
        subtitle="Live queue columns for the selected facility. Counts come from BFF queue stats and appointment list — not simulated dashboards."
      >
        <div className="mb-4">
          <Link
            href="/facility-operations"
            className="inline-flex items-center gap-1 text-xs font-semibold text-impilo-700 hover:text-impilo-900"
          >
            <ArrowLeft className="h-3.5 w-3.5" aria-hidden />
            Facility operations hub
          </Link>
        </div>

        {!fid ? (
          <div className="rounded-xl border border-amber-200 bg-amber-50/80 p-6 text-sm text-amber-950">
            Select a facility to load patient flow.
          </div>
        ) : loading ? (
          <div className="flex items-center gap-2 py-16 text-sm text-slate-500">
            <Activity className="h-5 w-5 animate-pulse text-impilo-500" aria-hidden />
            Loading flow data…
          </div>
        ) : (
          <div className="space-y-6">
            {summary}
            <div className="grid gap-4 lg:grid-cols-3">
              <FlowColumn
                title="Waiting"
                tone="border-amber-100"
                entries={w}
                empty="No WAITING entries returned for this facility."
              />
              <FlowColumn
                title="Called"
                tone="border-impilo-100"
                entries={c}
                empty="No CALLED entries — patients not currently paged."
              />
              <FlowColumn
                title="In service"
                tone="border-emerald-100"
                entries={p}
                empty="No IN_PROGRESS entries on the queue snapshot."
              />
            </div>

            <div>
              <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-600">
                PCT journey lanes (waiting by queue type)
              </h3>
              <p className="mt-1 text-[11px] text-slate-500">
                Each tile counts WAITING items for the queue type resolved by the BFF from PCT. Zeros are valid when no
                queue exists for that lane.
              </p>
              <dl className="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                {[
                  { title: "Triage", n: laneTriage.data?.data?.length ?? 0 },
                  { title: "Consultation", n: laneConsult.data?.data?.length ?? 0 },
                  { title: "Laboratory", n: laneLab.data?.data?.length ?? 0 },
                  { title: "Pharmacy", n: lanePharm.data?.data?.length ?? 0 },
                ].map((lane) => (
                  <div key={lane.title} className="rounded-xl border border-slate-200 bg-white p-3">
                    <dt className="text-[10px] font-semibold uppercase text-slate-500">{lane.title}</dt>
                    <dd className="text-lg font-bold text-slate-900">{lane.n}</dd>
                  </div>
                ))}
              </dl>
            </div>

            <p className="text-[11px] text-slate-500">
              For per-patient PCT timeline composition (encounters, orders, referrals), use the chart timeline; this
              board stays queue-first. Pharmacy, imaging, and payment coverage still have dedicated work routes.
            </p>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
