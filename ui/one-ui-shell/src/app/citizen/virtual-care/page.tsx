"use client";

/**
 * My virtual care requests — the citizen's own teleconsult requests (strictly
 * self-scoped server-side via X-Actor-ID). Statuses map to the real PCT referral
 * lifecycle: SUBMITTED = waiting in the virtual hospital's pool; ACCEPTED = a provider
 * has picked it up; COMPLETED/DECLINED close the loop.
 */

import Link from "next/link";
import { Loader2, MonitorSmartphone, Plus } from "lucide-react";
import { useMyVirtualCareRequests, type VirtualCareRequestRow } from "@/hooks/queries/useVirtualCare";

const STATUS_STYLE: Record<string, string> = {
  SUBMITTED: "bg-amber-100 text-amber-700",
  ROUTED: "bg-amber-100 text-amber-700",
  ACCEPTED: "bg-teal-100 text-teal-700",
  SCHEDULED: "bg-teal-100 text-teal-700",
  IN_REVIEW: "bg-sky-100 text-sky-700",
  RESPONDED: "bg-sky-100 text-sky-700",
  COMPLETED: "bg-slate-100 text-slate-600",
  DECLINED: "bg-rose-100 text-rose-700",
};

const STATUS_HELP: Record<string, string> = {
  SUBMITTED: "Waiting in the virtual hospital's pool — a provider will accept it.",
  ROUTED: "Waiting in the virtual hospital's pool — a provider will accept it.",
  ACCEPTED: "A provider has accepted your request.",
  SCHEDULED: "Your consultation has been scheduled.",
  DECLINED: "This request was declined — the provider's reason is on record.",
  COMPLETED: "This consultation is complete.",
};

function RequestCard({ request }: { request: VirtualCareRequestRow }) {
  const status = (request.status ?? "SUBMITTED").toUpperCase();
  return (
    <li className="rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-medium text-foreground">
          {request.virtualHospital?.name ?? request.specialty ?? "Teleconsult request"}
        </span>
        <span
          className={`rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_STYLE[status] ?? "bg-slate-100 text-slate-600"}`}
        >
          {status}
        </span>
        {request.modality && (
          <span className="text-xs text-muted-foreground">{request.modality}</span>
        )}
      </div>
      {request.reason && <p className="mt-1 text-sm text-muted-foreground">{request.reason}</p>}
      <p className="mt-1 text-xs text-muted-foreground">
        {STATUS_HELP[status] ?? "Being handled on the teleconsult service."}
      </p>
      {request.createdAt && (
        <p className="mt-1 text-xs text-muted-foreground">
          Requested {new Date(request.createdAt).toLocaleString()}
        </p>
      )}
    </li>
  );
}

export default function CitizenVirtualCarePage() {
  const requests = useMyVirtualCareRequests();
  const rows = requests.data?.data?.requests ?? [];

  return (
    <div className="mx-auto max-w-3xl space-y-6 p-6">
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-center gap-3">
          <MonitorSmartphone className="h-7 w-7 text-teal-600" aria-hidden />
          <div>
            <h1 className="text-2xl font-semibold text-foreground">My virtual care requests</h1>
            <p className="text-sm text-muted-foreground">
              Teleconsult requests you have made to Zimbabwe&apos;s virtual hospitals.
            </p>
          </div>
        </div>
        <Link
          href="/discover/virtual-care"
          className="inline-flex items-center gap-1 rounded-lg bg-teal-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-teal-700"
        >
          <Plus className="h-3.5 w-3.5" aria-hidden /> New request
        </Link>
      </header>

      {requests.isLoading && (
        <p className="inline-flex items-center gap-2 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> Loading your requests…
        </p>
      )}

      {requests.isError && !requests.isLoading && (
        <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-4 text-sm">
          <p className="font-medium text-foreground">We couldn&apos;t load your virtual care requests.</p>
          <button
            type="button"
            onClick={() => requests.refetch()}
            className="mt-2 rounded-md border border-border px-3 py-1.5 text-xs font-medium hover:border-primary/30"
          >
            Try again
          </button>
        </div>
      )}

      {!requests.isLoading && !requests.isError && (
        rows.length === 0 ? (
          <div className="rounded-xl border border-dashed border-border p-8 text-center text-sm text-muted-foreground">
            You have no virtual care requests yet.{" "}
            <Link href="/discover/virtual-care" className="font-medium text-teal-700 hover:underline">
              Find a virtual hospital
            </Link>{" "}
            to get started.
          </div>
        ) : (
          <ul className="space-y-3">
            {rows.map((request) => (
              <RequestCard key={request.id} request={request} />
            ))}
          </ul>
        )
      )}
    </div>
  );
}
