"use client";

/**
 * Specialist teleconsult worklist — the Stage-3/4 receiving surface for a virtual
 * specialist (7-stage lifecycle worklist types "assigned to me" / "facility service
 * queue"). Two worklist views:
 *
 *  - Facility worklist: submitted teleconsult referrals for the facility+specialty.
 *  - Virtual hospital pools: the pool-scoped queues of the ROUTABLE virtual hospitals
 *    (specialty-pool routing seams), fetched via the governed BFF pool-queue endpoint.
 *
 * Both share the governed Accept / Decline actions (audit + referrer notification)
 * and a jump into the session.
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import { ArrowLeft, Stethoscope, Clock, Loader2, Video } from "lucide-react";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import {
  useTelemedicineSpecialtyWorkbench,
  useAcceptTeleconsultSession,
  useDeclineTeleconsultSession,
} from "@/hooks/queries/useTelemedicine";
import { useTeleconsultPoolQueue } from "@/hooks/queries/useVirtualCare";
import { useVirtualHospitalDirectory } from "@/hooks/queries/useTelemedicineOperatingModel";

function extractItems(payload: unknown): Array<Record<string, unknown>> {
  if (Array.isArray(payload)) return payload as Array<Record<string, unknown>>;
  if (payload && typeof payload === "object") {
    const obj = payload as Record<string, unknown>;
    for (const key of ["items", "content", "referrals", "data"]) {
      const inner = obj[key];
      if (Array.isArray(inner)) return inner as Array<Record<string, unknown>>;
    }
  }
  return [];
}

const URGENCY_STYLE: Record<string, string> = {
  EMERGENCY: "bg-red-100 text-red-700",
  URGENT: "bg-orange-100 text-orange-700",
  ROUTINE: "bg-teal-100 text-teal-700",
};

function waitingLabel(item: Record<string, unknown>): string | null {
  const raw = item.submitted_at ?? item.submittedAt ?? item.created_at ?? item.createdAt;
  if (!raw) return null;
  const t = new Date(String(raw)).getTime();
  if (Number.isNaN(t)) return null;
  const mins = Math.max(0, Math.round((Date.now() - t) / 60000));
  if (mins < 60) return `${mins}m waiting`;
  return `${Math.floor(mins / 60)}h ${mins % 60}m waiting`;
}

interface ReferralActions {
  acceptPending: boolean;
  declinePending: boolean;
  onAccept: (id: string) => void;
  onDecline: (id: string, reason: string) => void;
}

/** Shared referral list with governed Accept / Decline — used by both worklist views. */
function ReferralList({
  items,
  actions,
  emptyLabel,
}: {
  items: Array<Record<string, unknown>>;
  actions: ReferralActions;
  emptyLabel: string;
}) {
  const [declineFor, setDeclineFor] = useState<string | null>(null);
  const [declineReason, setDeclineReason] = useState("");

  if (items.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-slate-300 p-8 text-center text-sm text-slate-500">
        {emptyLabel}
      </div>
    );
  }

  return (
    <ul className="space-y-3">
      {items.map((item, index) => {
        const id = String(item.id ?? item.referralId ?? index);
        const urgency = String(item.urgency ?? "ROUTINE").toUpperCase();
        const waiting = waitingLabel(item);
        return (
          <li key={id} className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium text-slate-800">
                    {String(item.specialty ?? item.clinical_question ?? item.clinicalQuestion ?? "Teleconsult referral")}
                  </span>
                  <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${URGENCY_STYLE[urgency] ?? "bg-slate-100 text-slate-600"}`}>
                    {urgency}
                  </span>
                  {waiting && (
                    <span className="inline-flex items-center gap-1 text-xs text-slate-400">
                      <Clock className="h-3 w-3" /> {waiting}
                    </span>
                  )}
                </div>
                <p className="mt-1 text-sm text-slate-600">
                  {String(item.reason ?? item.clinical_summary ?? item.clinicalSummary ?? "")}
                </p>
                <p className="mt-1 text-xs text-slate-400">
                  From {String(item.referred_by_name ?? item.referredByName ?? "referring clinician")}
                  {item.origin_facility_name ? ` · ${String(item.origin_facility_name)}` : ""}
                </p>
              </div>
              <div className="flex shrink-0 flex-col items-end gap-1">
                <button
                  type="button"
                  onClick={() => actions.onAccept(id)}
                  disabled={actions.acceptPending}
                  className="rounded-lg bg-teal-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-teal-700 disabled:opacity-50"
                >
                  Accept
                </button>
                <button
                  type="button"
                  onClick={() => {
                    setDeclineFor(declineFor === id ? null : id);
                    setDeclineReason("");
                  }}
                  className="rounded-lg border border-slate-300 px-3 py-1.5 text-xs font-medium text-slate-600 hover:bg-slate-50"
                >
                  Decline
                </button>
                <Link
                  href={`/telemedicine/session/${id}`}
                  className="inline-flex items-center gap-1 text-xs font-medium text-teal-600 hover:underline"
                >
                  <Video className="h-3 w-3" /> Session
                </Link>
              </div>
            </div>

            {declineFor === id && (
              <div className="mt-3 rounded-lg border border-slate-200 bg-slate-50 p-3">
                <textarea
                  value={declineReason}
                  onChange={(e) => setDeclineReason(e.target.value)}
                  rows={2}
                  placeholder="Reason for declining (required)"
                  className="w-full rounded-lg border border-slate-300 px-2 py-1.5 text-sm"
                />
                <div className="mt-2 flex justify-end gap-2">
                  <button
                    type="button"
                    onClick={() => setDeclineFor(null)}
                    className="rounded-lg px-3 py-1.5 text-xs text-slate-500 hover:bg-slate-100"
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      if (!declineReason.trim()) return;
                      actions.onDecline(id, declineReason.trim());
                      setDeclineFor(null);
                      setDeclineReason("");
                    }}
                    disabled={!declineReason.trim() || actions.declinePending}
                    className="rounded-lg bg-rose-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-rose-700 disabled:opacity-50"
                  >
                    Submit decline
                  </button>
                </div>
              </div>
            )}
          </li>
        );
      })}
    </ul>
  );
}

function FacilityWorklistPanel({ actions }: { actions: ReferralActions }) {
  const facility = useFacilityStore((s) => s.facility);
  const [specialty, setSpecialty] = useState("");
  const [draftSpecialty, setDraftSpecialty] = useState("");
  const workbench = useTelemedicineSpecialtyWorkbench({ facilityId: facility?.id, specialty: specialty || undefined });
  const items = useMemo(() => extractItems(workbench.data?.data), [workbench.data?.data]);

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-2">
        <label className="text-sm text-slate-600">
          Specialty
          <input
            value={draftSpecialty}
            onChange={(e) => setDraftSpecialty(e.target.value)}
            placeholder="e.g. DERMATOLOGY"
            className="mt-1 block rounded-lg border border-slate-300 px-3 py-2 text-sm"
          />
        </label>
        <button
          type="button"
          onClick={() => setSpecialty(draftSpecialty.trim())}
          className="rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700"
        >
          Filter
        </button>
      </div>

      {workbench.isLoading ? (
        <p className="inline-flex items-center gap-2 text-sm text-slate-400">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading worklist…
        </p>
      ) : (
        <ReferralList
          items={items}
          actions={actions}
          emptyLabel="No submitted teleconsult referrals in this queue."
        />
      )}
    </div>
  );
}

interface PoolOption {
  poolId: string;
  hospitalName: string;
}

function PoolQueuePanel({ actions }: { actions: ReferralActions }) {
  const directory = useVirtualHospitalDirectory();
  const pools: PoolOption[] = useMemo(() => {
    const out: PoolOption[] = [];
    for (const vh of directory.data ?? []) {
      if (vh.substrateStatus !== "ROUTABLE_VIA_EXISTING_SEAMS") continue;
      for (const seam of vh.currentRoutingSeams ?? []) {
        if (seam.routingType === "SPECIALTY_POOL" && seam.targetRef) {
          out.push({ poolId: seam.targetRef, hospitalName: vh.name });
        }
      }
    }
    return out;
  }, [directory.data]);

  const [selectedPool, setSelectedPool] = useState<string>("");
  const activePool = selectedPool || pools[0]?.poolId || "";
  const queue = useTeleconsultPoolQueue(activePool || null);
  const items = useMemo(() => extractItems(queue.data?.data), [queue.data?.data]);
  const activeHospital = pools.find((p) => p.poolId === activePool)?.hospitalName;

  return (
    <div className="space-y-4">
      <p className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600">
        Citizen and facility teleconsult requests routed to a virtual hospital&apos;s specialty
        pool. Any authorized telemedicine provider can accept from a pool — per-pool duty
        rosters arrive in Wave 2.
      </p>

      {directory.isLoading ? (
        <p className="inline-flex items-center gap-2 text-sm text-slate-400">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading virtual hospitals…
        </p>
      ) : pools.length === 0 ? (
        <div className="rounded-xl border border-dashed border-slate-300 p-8 text-center text-sm text-slate-500">
          No routable virtual-hospital pools are configured.
        </div>
      ) : (
        <>
          <label className="block text-sm text-slate-600">
            Virtual hospital pool
            <select
              value={activePool}
              onChange={(e) => setSelectedPool(e.target.value)}
              className="mt-1 block w-full max-w-md rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
            >
              {pools.map((pool) => (
                <option key={pool.poolId} value={pool.poolId}>
                  {pool.hospitalName} ({pool.poolId})
                </option>
              ))}
            </select>
          </label>

          {queue.isLoading ? (
            <p className="inline-flex items-center gap-2 text-sm text-slate-400">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading pool queue…
            </p>
          ) : queue.isError ? (
            <div className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">
              The pool queue could not be loaded.
              <button
                type="button"
                onClick={() => queue.refetch()}
                className="ml-2 rounded-md border border-rose-300 px-2 py-1 text-xs font-medium hover:bg-rose-100"
              >
                Try again
              </button>
            </div>
          ) : (
            <ReferralList
              items={items}
              actions={actions}
              emptyLabel={`No waiting requests in the ${activeHospital ?? activePool} pool.`}
            />
          )}
        </>
      )}
    </div>
  );
}

export default function SpecialistWorklistPage() {
  const facility = useFacilityStore((s) => s.facility);
  const accept = useAcceptTeleconsultSession();
  const decline = useDeclineTeleconsultSession();
  const [view, setView] = useState<"facility" | "pools">("facility");

  const actions: ReferralActions = {
    acceptPending: accept.isPending,
    declinePending: decline.isPending,
    onAccept: (id) => {
      accept.mutate({
        id,
        ...(facility ? { receivingFacilityId: facility.id, receivingFacilityName: facility.name } : {}),
      });
    },
    onDecline: (id, reason) => {
      decline.mutate({ id, reason });
    },
  };

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-6">
      <header>
        <Link
          href="/work/telemedicine"
          className="mb-2 inline-flex items-center gap-1 text-sm text-slate-500 hover:text-teal-700"
        >
          <ArrowLeft className="h-4 w-4" /> Telemedicine operating model
        </Link>
        <div className="flex items-center gap-3">
          <Stethoscope className="h-7 w-7 text-teal-600" />
          <div>
            <h1 className="text-2xl font-semibold text-slate-900">Specialist worklist</h1>
            <p className="text-sm text-slate-500">
              Submitted teleconsult referrals awaiting your review.
              {facility ? ` Facility: ${facility.name}.` : " No facility context selected."}
            </p>
          </div>
        </div>
      </header>

      <div role="tablist" aria-label="Worklist view" className="flex gap-1 rounded-lg border border-slate-200 bg-slate-50 p-1">
        <button
          type="button"
          role="tab"
          aria-selected={view === "facility"}
          onClick={() => setView("facility")}
          className={`rounded-md px-3 py-1.5 text-sm font-medium ${
            view === "facility" ? "bg-white text-teal-700 shadow-sm" : "text-slate-500 hover:text-slate-700"
          }`}
        >
          Facility worklist
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={view === "pools"}
          onClick={() => setView("pools")}
          className={`rounded-md px-3 py-1.5 text-sm font-medium ${
            view === "pools" ? "bg-white text-teal-700 shadow-sm" : "text-slate-500 hover:text-slate-700"
          }`}
        >
          Virtual hospital pools
        </button>
      </div>

      {view === "facility" ? (
        <FacilityWorklistPanel actions={actions} />
      ) : (
        <PoolQueuePanel actions={actions} />
      )}
    </div>
  );
}
