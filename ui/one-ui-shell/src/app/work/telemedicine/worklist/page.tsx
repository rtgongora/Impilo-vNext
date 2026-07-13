"use client";

/**
 * Specialist teleconsult worklist — the Stage-3/4 receiving surface for a virtual
 * specialist (7-stage lifecycle worklist types "assigned to me" / "facility service
 * queue"). Submitted teleconsult referrals for the facility+specialty, with governed
 * Accept / Decline (audit + referrer notification) and a jump into the session.
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

export default function SpecialistWorklistPage() {
  const facility = useFacilityStore((s) => s.facility);
  const [specialty, setSpecialty] = useState("");
  const [draftSpecialty, setDraftSpecialty] = useState("");
  const workbench = useTelemedicineSpecialtyWorkbench({ facilityId: facility?.id, specialty: specialty || undefined });
  const accept = useAcceptTeleconsultSession();
  const decline = useDeclineTeleconsultSession();

  const items = useMemo(() => extractItems(workbench.data?.data), [workbench.data?.data]);
  const [declineFor, setDeclineFor] = useState<string | null>(null);
  const [declineReason, setDeclineReason] = useState("");

  function handleAccept(id: string) {
    if (!facility) return;
    accept.mutate({ id, receivingFacilityId: facility.id, receivingFacilityName: facility.name });
  }

  function handleDecline(id: string) {
    if (!declineReason.trim()) return;
    decline.mutate(
      { id, reason: declineReason.trim() },
      {
        onSuccess: () => {
          setDeclineFor(null);
          setDeclineReason("");
        },
      },
    );
  }

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
      ) : items.length === 0 ? (
        <div className="rounded-xl border border-dashed border-slate-300 p-8 text-center text-sm text-slate-500">
          No submitted teleconsult referrals in this queue.
        </div>
      ) : (
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
                      onClick={() => handleAccept(id)}
                      disabled={accept.isPending}
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
                        onClick={() => handleDecline(id)}
                        disabled={!declineReason.trim() || decline.isPending}
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
      )}
    </div>
  );
}
