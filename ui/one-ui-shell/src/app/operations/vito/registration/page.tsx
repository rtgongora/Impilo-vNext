"use client";

import Link from "next/link";
import { useState } from "react";
import { UserPlus, Search, ChevronLeft, ChevronRight, Shield, AlertTriangle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useClientRegistryClients,
  type IdentityStatus,
  type ClientVerificationState,
  type ClientRegistrySummary,
} from "@/hooks/queries/useClientRegistry";

const LIFECYCLE_STYLES: Record<IdentityStatus, string> = {
  DRAFT: "bg-neutral-100 text-muted-foreground border-border",
  PROVISIONAL: "bg-info-soft text-primary-hover border-info/25",
  REGISTERED: "bg-info-soft text-primary-hover border-info/25",
  PENDING_VERIFICATION: "bg-warning-soft text-warning-foreground border-warning/35",
  PENDING_MATCH_REVIEW: "bg-orange-50 text-orange-700 border-orange-200",
  VERIFIED: "bg-success-soft text-primary-hover border-success/25",
  ACTIVE: "bg-green-50 text-green-700 border-green-200",
  FLAGGED_FOR_REVIEW: "bg-danger-soft text-danger border-danger/28",
  RESTRICTED: "bg-danger-soft text-danger border-danger/28",
  INACTIVE: "bg-neutral-100 text-muted-foreground border-border",
  DECEASED: "bg-rose-100 text-rose-800 border-rose-300",
  MERGED: "bg-warning-soft text-warning-foreground border-warning/35",
};

const VERIFICATION_STYLES: Record<ClientVerificationState, string> = {
  UNVERIFIED: "bg-neutral-100 text-muted-foreground",
  SELF_ASSERTED: "bg-info-soft text-primary",
  PROVIDER_CAPTURED: "bg-info-soft text-indigo-600",
  PARTIALLY_VERIFIED: "bg-warning-soft text-warning-foreground",
  VERIFIED: "bg-success-soft text-primary-hover",
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
      className="flex items-center justify-between gap-4 px-5 py-4 hover:bg-background transition-colors"
    >
      <div className="min-w-0 flex-1 space-y-1">
        <div className="flex flex-wrap items-center gap-2">
          <p className="font-medium text-foreground truncate">{client.displayName}</p>
          <LifecycleBadge status={client.lifecycleStatus as IdentityStatus} />
          <VerificationBadge state={client.verificationStatus as ClientVerificationState} />
        </div>
        <div className="flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
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
      <span className="shrink-0 text-xs text-muted-foreground">View →</span>
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

  const results = useClientRegistryClients({ query: submittedQuery, status: statusParam, page, size: PAGE_SIZE });
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
              className="text-sm text-muted-foreground underline-offset-2 hover:text-foreground hover:underline"
            >
              ← Identity operations
            </Link>
            <Link
              href="/operations/vito/registration/new"
              className="inline-flex items-center gap-2 rounded-xl bg-primary-hover px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700"
            >
              <UserPlus className="h-4 w-4" />
              New registration
            </Link>
          </div>

          <div className="rounded-2xl border border-border bg-card p-5 space-y-4">
            <div className="flex items-center gap-2">
              <Search className="h-4 w-4 text-muted-foreground" />
              <h2 className="text-sm font-semibold text-foreground">Search</h2>
            </div>

            <div className="flex flex-wrap gap-3">
              <input
                className="flex-1 min-w-[200px] rounded-xl border border-border px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                placeholder="Name, Health ID, national ID…"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && handleSearch()}
              />
              <select
                className="rounded-xl border border-border px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
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
                className="inline-flex items-center gap-1.5 rounded-xl bg-primary-hover px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
              >
                <Search className="h-4 w-4" />
                {results.isFetching ? "Searching…" : "Search"}
              </button>
            </div>

            {results.isError && (
              <div className="flex items-center gap-2 rounded-2xl bg-warning-soft p-4 text-sm text-warning-foreground">
                <AlertTriangle className="h-4 w-4 flex-shrink-0" />
                Search failed. Verify that the client registry service is reachable.
              </div>
            )}
          </div>

          <div className="rounded-2xl border border-border bg-card">
            <div className="flex items-center justify-between border-b border-border px-5 py-3">
              <h2 className="text-sm font-semibold text-foreground">
                Results
                {results.data?.data && (
                  <span className="ml-2 rounded bg-neutral-100 px-2 py-0.5 text-xs font-normal text-muted-foreground">
                    {results.data.data.totalElements}
                  </span>
                )}
              </h2>
            </div>

            {results.isLoading ? (
              <div className="p-8 text-center text-sm text-muted-foreground">Loading…</div>
            ) : items.length === 0 ? (
              <div className="p-8 text-center text-sm text-muted-foreground">
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
                className="inline-flex items-center gap-1 rounded-lg border border-border px-3 py-1.5 text-sm text-muted-foreground hover:border-border disabled:opacity-40"
              >
                <ChevronLeft className="h-4 w-4" />
                Previous
              </button>
              <span className="text-sm text-muted-foreground">
                Page {page + 1}{totalPages > 0 ? ` of ${totalPages}` : ""}
              </span>
              <button
                type="button"
                disabled={!hasNext}
                onClick={() => setPage((p) => p + 1)}
                className="inline-flex items-center gap-1 rounded-lg border border-border px-3 py-1.5 text-sm text-muted-foreground hover:border-border disabled:opacity-40"
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
