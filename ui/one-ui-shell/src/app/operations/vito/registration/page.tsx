"use client";

import Link from "next/link";
import { useState } from "react";
import { UserPlus, Search, ChevronLeft, ChevronRight, Shield, AlertTriangle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useClientRegistrySearch,
  type IdentityStatus,
  type ClientVerificationState,
  type ClientRegistrySummary,
} from "@/hooks/queries/useVitoClientRegistry";

const LIFECYCLE_STYLES: Record<IdentityStatus, string> = {
  DRAFT: "bg-gray-100 text-gray-600 border-gray-200",
  PROVISIONAL: "bg-blue-50 text-blue-700 border-blue-200",
  REGISTERED: "bg-indigo-50 text-indigo-700 border-indigo-200",
  PENDING_VERIFICATION: "bg-amber-50 text-amber-700 border-amber-200",
  PENDING_MATCH_REVIEW: "bg-orange-50 text-orange-700 border-orange-200",
  VERIFIED: "bg-emerald-50 text-emerald-700 border-emerald-200",
  ACTIVE: "bg-green-50 text-green-700 border-green-200",
  FLAGGED_FOR_REVIEW: "bg-red-50 text-red-700 border-red-200",
  RESTRICTED: "bg-rose-50 text-rose-700 border-rose-200",
  INACTIVE: "bg-gray-100 text-gray-500 border-gray-200",
  DECEASED: "bg-rose-100 text-rose-800 border-rose-300",
  MERGED: "bg-purple-50 text-purple-700 border-purple-200",
};

const VERIFICATION_STYLES: Record<ClientVerificationState, string> = {
  UNVERIFIED: "bg-gray-100 text-gray-500",
  SELF_ASSERTED: "bg-blue-50 text-blue-600",
  PROVIDER_CAPTURED: "bg-indigo-50 text-indigo-600",
  PARTIALLY_VERIFIED: "bg-amber-50 text-amber-700",
  VERIFIED: "bg-emerald-50 text-emerald-700",
  REVIEW_REQUIRED: "bg-orange-50 text-orange-700",
};

const STATUS_OPTIONS: Array<IdentityStatus | ""> = [
  "",
  "DRAFT",
  "PROVISIONAL",
  "REGISTERED",
  "PENDING_VERIFICATION",
  "PENDING_MATCH_REVIEW",
  "VERIFIED",
  "ACTIVE",
  "FLAGGED_FOR_REVIEW",
  "RESTRICTED",
  "INACTIVE",
  "DECEASED",
  "MERGED",
];

function LifecycleBadge({ status }: { status: IdentityStatus }) {
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium ${LIFECYCLE_STYLES[status]}`}
    >
      {status.replace(/_/g, " ")}
    </span>
  );
}

function VerificationBadge({ state }: { state: ClientVerificationState }) {
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-lg px-2 py-0.5 text-xs font-medium ${VERIFICATION_STYLES[state]}`}
    >
      <Shield className="h-3 w-3" />
      {state.replace(/_/g, " ")}
    </span>
  );
}

function RegistrationRow({ client }: { client: ClientRegistrySummary }) {
  return (
    <Link
      href={`/operations/vito/registration/new?healthId=${encodeURIComponent(client.healthId)}`}
      className="flex items-center justify-between gap-4 px-5 py-4 hover:bg-gray-50 transition-colors"
    >
      <div className="min-w-0 flex-1 space-y-1">
        <div className="flex flex-wrap items-center gap-2">
          <p className="font-medium text-gray-900 truncate">{client.displayName}</p>
          <LifecycleBadge status={client.lifecycleStatus} />
          <VerificationBadge state={client.verificationStatus} />
        </div>
        <div className="flex flex-wrap items-center gap-3 text-xs text-gray-500">
          <span className="font-mono">{client.healthId}</span>
          {client.latestRegistrationType && (
            <span>{client.latestRegistrationType.replace(/_/g, " ")}</span>
          )}
          {client.openStewardshipActions > 0 && (
            <span className="text-amber-600">
              {client.openStewardshipActions} open action{client.openStewardshipActions !== 1 ? "s" : ""}
            </span>
          )}
          {client.openMatches > 0 && (
            <span className="text-orange-600">
              {client.openMatches} pending match{client.openMatches !== 1 ? "es" : ""}
            </span>
          )}
        </div>
      </div>
      <span className="shrink-0 text-xs text-gray-400">View →</span>
    </Link>
  );
}

export default function RegistrationListPage() {
  const [query, setQuery] = useState("");
  const [submittedQuery, setSubmittedQuery] = useState<string | undefined>(undefined);
  const [statusFilter, setStatusFilter] = useState<IdentityStatus | "">("");
  const [page, setPage] = useState(0);
  const PAGE_SIZE = 20;

  const statusParam = statusFilter !== "" ? statusFilter : undefined;

  const results = useClientRegistrySearch(submittedQuery, statusParam, undefined, page, PAGE_SIZE);
  const items = results.data?.data?.items ?? [];
  const totalPages = results.data?.data?.totalPages ?? 0;
  const hasNext = results.data?.data?.hasNext ?? false;

  function handleSearch() {
    setPage(0);
    setSubmittedQuery(query.trim() || undefined);
  }

  return (
    <AppLayout>
      <PageShell
        title="Client registrations"
        subtitle="Search the client registry and start new identity registrations"
        icon={<UserPlus className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <Link
              href="/operations/vito"
              className="text-sm text-gray-600 underline-offset-2 hover:text-gray-900 hover:underline"
            >
              ← Identity operations
            </Link>
            <Link
              href="/operations/vito/registration/new"
              className="inline-flex items-center gap-2 rounded-xl bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700"
            >
              <UserPlus className="h-4 w-4" />
              New registration
            </Link>
          </div>

          <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
            <div className="flex items-center gap-2">
              <Search className="h-4 w-4 text-gray-500" />
              <h2 className="text-sm font-semibold text-gray-900">Search</h2>
            </div>

            <div className="flex flex-wrap gap-3">
              <input
                className="flex-1 min-w-[200px] rounded-xl border border-gray-300 px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                placeholder="Name, Health ID, national ID…"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && handleSearch()}
              />
              <select
                className="rounded-xl border border-gray-300 px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                value={statusFilter}
                onChange={(e) => {
                  setStatusFilter(e.target.value as IdentityStatus | "");
                  setPage(0);
                }}
              >
                {STATUS_OPTIONS.map((s) => (
                  <option key={s} value={s}>
                    {s === "" ? "All statuses" : s.replace(/_/g, " ")}
                  </option>
                ))}
              </select>
              <button
                type="button"
                disabled={results.isFetching}
                onClick={handleSearch}
                className="inline-flex items-center gap-1.5 rounded-xl bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
              >
                <Search className="h-4 w-4" />
                {results.isFetching ? "Searching…" : "Search"}
              </button>
            </div>

            {results.isError && (
              <div className="flex items-center gap-2 rounded-2xl bg-amber-50 p-4 text-sm text-amber-700">
                <AlertTriangle className="h-4 w-4 flex-shrink-0" />
                Search failed. Verify that the client registry service is reachable.
              </div>
            )}
          </div>

          <div className="rounded-2xl border border-gray-200 bg-white">
            <div className="flex items-center justify-between border-b border-gray-100 px-5 py-3">
              <h2 className="text-sm font-semibold text-gray-900">
                Results
                {results.data?.data && (
                  <span className="ml-2 rounded bg-gray-100 px-2 py-0.5 text-xs font-normal text-gray-500">
                    {results.data.data.totalElements}
                  </span>
                )}
              </h2>
            </div>

            {results.isLoading ? (
              <div className="p-8 text-center text-sm text-gray-500">Loading…</div>
            ) : items.length === 0 ? (
              <div className="p-8 text-center text-sm text-gray-500">
                No registrations found. Use the search above or start a new registration.
              </div>
            ) : (
              <div className="divide-y divide-gray-100">
                {items.map((client) => (
                  <RegistrationRow key={client.healthId} client={client} />
                ))}
              </div>
            )}
          </div>

          {(page > 0 || hasNext) && (
            <div className="flex items-center justify-between">
              <button
                type="button"
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="inline-flex items-center gap-1 rounded-lg border border-gray-200 px-3 py-1.5 text-sm text-gray-600 hover:border-gray-300 disabled:opacity-40"
              >
                <ChevronLeft className="h-4 w-4" />
                Previous
              </button>
              <span className="text-sm text-gray-500">
                Page {page + 1}{totalPages > 0 ? ` of ${totalPages}` : ""}
              </span>
              <button
                type="button"
                disabled={!hasNext}
                onClick={() => setPage((p) => p + 1)}
                className="inline-flex items-center gap-1 rounded-lg border border-gray-200 px-3 py-1.5 text-sm text-gray-600 hover:border-gray-300 disabled:opacity-40"
              >
                Next
                <ChevronRight className="h-4 w-4" />
              </button>
            </div>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
