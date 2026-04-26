"use client";

import Link from "next/link";
import { useMemo } from "react";
import {
  Activity,
  ArrowRight,
  Clock,
  MapPin,
  Route,
  Users,
  Video,
} from "lucide-react";
import { useEncounters } from "@/hooks/queries/useEncounters";
import { useReferrals } from "@/hooks/queries/useReferrals";
import { useTelemedicineSessions } from "@/hooks/queries/useTelemedicine";
import { useAdmissions } from "@/hooks/queries/useInpatient";
import { useQueueEntries, type QueueEntryResource } from "@/hooks/queries/useQueue";
import { useLabOrders } from "@/hooks/queries/useLabOrders";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useWorkspaceStore } from "@/hooks/useWorkspaceStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import { getQueuePatientId, formatQueueWaitTime, getQueueQueuedAt } from "@/lib/queue-workflows";

/**
 * PCT-aligned patient journey context composed from **real** Experience BFF sources
 * (queue, encounters, referrals, telemedicine, admissions, lab orders). No mock counts.
 */
export function PatientJourneyContextPanel({
  patientId,
  variant = "default",
}: {
  patientId: string;
  variant?: "default" | "compact";
}) {
  const facility = useFacilityStore((s) => s.facility);
  const workspace = useWorkspaceStore((s) => s.workspace);
  const { shift } = useShiftStore();

  const { data: queueData } = useQueueEntries({ facilityId: facility?.id });
  const { data: encountersData } = useEncounters(patientId);
  const { data: referralsData } = useReferrals(patientId);
  const { data: teleData } = useTelemedicineSessions({ patientId, facilityId: facility?.id });
  const { data: admissionsData } = useAdmissions(patientId);
  const { data: labData } = useLabOrders(patientId);

  const queueRows = useMemo(() => {
    const all = queueData?.data ?? [];
    return all.filter((e: QueueEntryResource) => getQueuePatientId(e) === patientId);
  }, [queueData?.data, patientId]);

  const encounters = encountersData?.data ?? [];
  const activeEncounter = encounters.find(
    (e) => e.attributes.status === "ACTIVE" || e.attributes.status === "IN_PROGRESS",
  );

  const referrals = referralsData?.data ?? [];
  const openReferrals = referrals.filter((r) => !["COMPLETED", "CANCELLED"].includes(String(r.attributes.status)));

  const sessions = teleData?.data ?? [];
  const openTele = sessions.filter((s) => ["SCHEDULED", "IN_PROGRESS"].includes(s.attributes.status));

  const admissionsRaw = (admissionsData as { data?: unknown } | undefined)?.data;
  const admissions = Array.isArray(admissionsRaw)
    ? (admissionsRaw as { id: string; attributes: Record<string, unknown> }[])
    : [];
  const activeAdmission = admissions.find(
    (a) => a.attributes.status === "ACTIVE" || a.attributes.status === "ADMITTED",
  );

  const labs = labData?.data ?? [];
  const pendingLabs = labs.filter((o) => ["ORDERED", "COLLECTED", "RESULTED"].includes(String(o.attributes.status)));

  const queueWait = useMemo(() => {
    const active = queueRows.find((q) => ["WAITING", "CALLED", "IN_PROGRESS"].includes(q.attributes.status));
    if (!active) return null;
    const at = getQueueQueuedAt(active);
    if (!at) return null;
    return formatQueueWaitTime(at);
  }, [queueRows]);

  const nextAction = useMemo(() => {
    if (activeEncounter) return { href: `/ehr/${patientId}/encounter/${activeEncounter.id}`, label: "Resume encounter" };
    if (queueRows.some((q) => q.attributes.status === "WAITING"))
      return { href: "/queue", label: "Patient is waiting in facility queue" };
    if (openReferrals.length > 0) return { href: `/ehr/${patientId}/consults`, label: "Referral follow-up" };
    if (pendingLabs.some((o) => o.attributes.status === "RESULTED"))
      return { href: `/ehr/${patientId}/orders`, label: "Results awaiting review" };
    if (openTele.length > 0) return { href: `/ehr/${patientId}/consults`, label: "Teleconsult in progress or scheduled" };
    if (activeAdmission) return { href: `/beds?patientId=${patientId}`, label: "Inpatient admission active" };
    return { href: `/ehr/${patientId}/vitals`, label: "Continue routine charting" };
  }, [activeAdmission, activeEncounter, openReferrals.length, openTele.length, patientId, pendingLabs, queueRows]);

  const hasAnySignal =
    !!activeEncounter ||
    queueRows.length > 0 ||
    openReferrals.length > 0 ||
    openTele.length > 0 ||
    !!activeAdmission ||
    pendingLabs.length > 0;

  if (variant === "compact") {
    return (
      <div className="flex flex-wrap items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2 text-xs text-slate-700">
        <Route className="h-3.5 w-3.5 shrink-0 text-impilo-500" />
        <span className="font-semibold text-slate-800">Journey</span>
        <span className="text-slate-500">
          {facility?.name ?? "Facility"}
          {workspace?.name ? ` · ${workspace.name}` : ""}
        </span>
        {queueWait && (
          <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2 py-0.5 text-[10px] font-medium text-amber-800">
            <Clock className="h-3 w-3" />
            Queue {queueWait}
          </span>
        )}
        <Link href={nextAction.href} className="ml-auto inline-flex items-center gap-1 font-medium text-impilo-600 hover:text-impilo-800">
          {nextAction.label}
          <ArrowRight className="h-3 w-3" />
        </Link>
      </div>
    );
  }

  return (
    <section className="rounded-2xl border border-slate-200 bg-gradient-to-br from-white via-slate-50/80 to-impilo-50/40 p-5 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-[10px] font-bold uppercase tracking-wider text-slate-500">Patient journey context</p>
          <h2 className="mt-1 text-base font-semibold text-slate-900">Operational picture (PCT-aligned)</h2>
          <p className="mt-1 max-w-2xl text-xs text-slate-600">
            Queue, encounter, referral, telemedicine, admission, and lab data are read from the Experience BFF. When PCT is online, the same
            patient identifiers underpin journey lifecycle upstream.
          </p>
        </div>
        <div className="text-right text-[11px] text-slate-500">
          <div className="inline-flex items-center gap-1">
            <MapPin className="h-3.5 w-3.5" />
            {facility?.name ?? "No facility selected"}
          </div>
          {workspace?.name && <div className="mt-0.5">{workspace.name}</div>}
          {shift && <div className="mt-0.5">Shift active</div>}
        </div>
      </div>

      {!hasAnySignal ? (
        <p className="mt-4 rounded-lg border border-dashed border-slate-200 bg-white/80 px-3 py-3 text-sm text-slate-600">
          No active queue row, open encounter, referrals, teleconsults, admission, or outstanding lab work surfaced for this patient in the
          current facility context. This is an honest empty state — not a simulated journey.
        </p>
      ) : (
        <dl className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <div className="rounded-xl border border-white/70 bg-white/90 p-3">
            <dt className="flex items-center gap-1 text-[10px] font-semibold uppercase tracking-wide text-slate-500">
              <Activity className="h-3.5 w-3.5" />
              Encounter
            </dt>
            <dd className="mt-1 text-sm text-slate-900">
              {activeEncounter ? (
                <>
                  <span className="font-medium">{activeEncounter.attributes.encounterType}</span>{" "}
                  <span className="text-slate-500">({activeEncounter.attributes.status})</span>
                  <div className="mt-2">
                    <Link
                      href={`/ehr/${patientId}/encounter/${activeEncounter.id}`}
                      className="text-xs font-medium text-impilo-600 hover:text-impilo-800"
                    >
                      Open encounter workspace
                    </Link>
                  </div>
                </>
              ) : (
                <span className="text-slate-500">No active encounter</span>
              )}
            </dd>
          </div>

          <div className="rounded-xl border border-white/70 bg-white/90 p-3">
            <dt className="flex items-center gap-1 text-[10px] font-semibold uppercase tracking-wide text-slate-500">
              <Clock className="h-3.5 w-3.5" />
              Queue / SLA
            </dt>
            <dd className="mt-1 text-sm text-slate-900">
              {queueRows.length === 0 ? (
                <span className="text-slate-500">Not on facility queue</span>
              ) : (
                <>
                  {queueRows.map((q) => (
                    <div key={q.id} className="text-xs text-slate-700">
                      <span className="font-medium">{q.attributes.status}</span>
                      {getQueueQueuedAt(q) ? (
                        <span className="text-slate-500"> · waiting {formatQueueWaitTime(getQueueQueuedAt(q))}</span>
                      ) : null}
                    </div>
                  ))}
                  <Link href="/queue" className="mt-2 inline-block text-xs font-medium text-impilo-600 hover:text-impilo-800">
                    Open queue board
                  </Link>
                </>
              )}
            </dd>
          </div>

          <div className="rounded-xl border border-white/70 bg-white/90 p-3">
            <dt className="flex items-center gap-1 text-[10px] font-semibold uppercase tracking-wide text-slate-500">
              <Users className="h-3.5 w-3.5" />
              Referrals / tasks
            </dt>
            <dd className="mt-1 text-sm text-slate-900">
              {openReferrals.length === 0 ? (
                <span className="text-slate-500">No open referrals</span>
              ) : (
                <>
                  <span className="font-medium">{openReferrals.length}</span> open
                  <div className="mt-2">
                    <Link href={`/ehr/${patientId}/consults`} className="text-xs font-medium text-impilo-600 hover:text-impilo-800">
                      Consults &amp; referrals
                    </Link>
                  </div>
                </>
              )}
            </dd>
          </div>

          <div className="rounded-xl border border-white/70 bg-white/90 p-3">
            <dt className="flex items-center gap-1 text-[10px] font-semibold uppercase tracking-wide text-slate-500">
              <Video className="h-3.5 w-3.5" />
              Telemedicine
            </dt>
            <dd className="mt-1 text-sm text-slate-900">
              {openTele.length === 0 ? (
                <span className="text-slate-500">No scheduled or live sessions</span>
              ) : (
                <>
                  <span className="font-medium">{openTele.length}</span> active / scheduled
                  <div className="mt-2">
                    <Link href={`/telemedicine?patientId=${encodeURIComponent(patientId)}`} className="text-xs font-medium text-impilo-600 hover:text-impilo-800">
                      Telemedicine hub
                    </Link>
                  </div>
                </>
              )}
            </dd>
          </div>

          <div className="rounded-xl border border-white/70 bg-white/90 p-3">
            <dt className="text-[10px] font-semibold uppercase tracking-wide text-slate-500">Admission</dt>
            <dd className="mt-1 text-sm text-slate-900">
              {activeAdmission ? (
                <>
                  <span className="font-medium">Admitted</span>
                  <div className="mt-2">
                    <Link href={`/beds?patientId=${patientId}`} className="text-xs font-medium text-impilo-600 hover:text-impilo-800">
                      Beds / ward view
                    </Link>
                  </div>
                </>
              ) : (
                <span className="text-slate-500">No active admission</span>
              )}
            </dd>
          </div>

          <div className="rounded-xl border border-white/70 bg-white/90 p-3">
            <dt className="text-[10px] font-semibold uppercase tracking-wide text-slate-500">OROS labs (chart)</dt>
            <dd className="mt-1 text-sm text-slate-900">
              <span className="font-medium">{pendingLabs.length}</span> orders in-flight or awaiting review
              <div className="mt-2">
                <Link href={`/ehr/${patientId}/orders`} className="text-xs font-medium text-impilo-600 hover:text-impilo-800">
                  Orders &amp; results
                </Link>
              </div>
            </dd>
          </div>
        </dl>
      )}

      <div className="mt-4 flex flex-wrap items-center justify-between gap-2 rounded-xl border border-impilo-100 bg-impilo-50/60 px-3 py-2">
        <p className="text-xs text-impilo-900">
          <span className="font-semibold">Next:</span> {nextAction.label}
        </p>
        <Link href={nextAction.href} className="inline-flex items-center gap-1 rounded-lg bg-impilo-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-impilo-700">
          Go
          <ArrowRight className="h-3.5 w-3.5" />
        </Link>
      </div>
    </section>
  );
}
