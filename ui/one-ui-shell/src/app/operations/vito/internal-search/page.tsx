"use client";

import Link from "next/link";
import { useState } from "react";
import { Search, User, AlertTriangle, X, Shield } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useInternalClientSearch,
  useInternalClientFull,
  type MaskedClient,
  type InternalClientFull,
} from "@/hooks/queries/useVitoInternalSearch";

function maskYear(dob: string | undefined) {
  if (!dob) return "—";
  const parsed = new Date(dob);
  if (isNaN(parsed.getTime())) return dob;
  return String(parsed.getFullYear());
}

function statusBadge(status: string) {
  const map: Record<string, string> = {
    ACTIVE: "bg-success-soft text-primary-hover border-success/25",
    INACTIVE: "bg-neutral-100 text-muted-foreground border-border",
    DECEASED: "bg-danger-soft text-danger border-danger/28",
    SUSPENDED: "bg-warning-soft text-warning-foreground border-warning/35",
  };
  const cls = map[status] ?? "bg-background text-muted-foreground border-border";
  return (
    <span className={`inline-block rounded-full border px-2.5 py-0.5 text-xs font-medium ${cls}`}>
      {status}
    </span>
  );
}

function assuranceBadge(level: string) {
  const map: Record<string, string> = {
    HIGH: "bg-success-soft text-primary-hover",
    MEDIUM: "bg-sky-50 text-sky-700",
    LOW: "bg-warning-soft text-warning-foreground",
    NONE: "bg-background text-muted-foreground",
  };
  const cls = map[level] ?? "bg-background text-muted-foreground";
  return (
    <span className={`inline-flex items-center gap-1 rounded-lg px-2 py-0.5 text-xs font-medium ${cls}`}>
      <Shield className="h-3 w-3" />
      {level}
    </span>
  );
}

function DetailPanel({ healthId, onClose }: { healthId: string; onClose: () => void }) {
  const query = useInternalClientFull(healthId);
  const client: InternalClientFull | undefined = query.data?.data;

  return (
    <div className="rounded-2xl border border-primary/25 bg-primary-soft p-5 space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <User className="h-4 w-4 text-primary" />
          <h2 className="text-sm font-semibold text-foreground">Full Client Record</h2>
          <span className="rounded bg-primary-soft px-2 py-0.5 text-xs text-primary-hover">{healthId}</span>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="rounded-lg p-1 text-muted-foreground hover:bg-card hover:text-foreground"
          aria-label="Close detail panel"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      {query.isLoading && (
        <p className="text-sm text-muted-foreground">Loading full record…</p>
      )}

      {query.isError && (
        <div className="flex items-center gap-2 rounded-2xl bg-warning-soft p-4 text-sm text-warning-foreground">
          <AlertTriangle className="h-4 w-4 flex-shrink-0" />
          Unable to load this client record. Access may require a higher assurance level.
        </div>
      )}

      {client && (
        <div className="rounded-2xl border border-border bg-card p-5 space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            {statusBadge(client.status)}
            {assuranceBadge(client.assuranceLevel)}
          </div>

          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <Field label="Health ID" value={client.healthId} mono />
            <Field label="Impilo ID" value={client.impiloId} mono />
            <Field label="Given name" value={client.givenName} />
            <Field label="Family name" value={client.familyName} />
            <Field label="Date of birth" value={client.dateOfBirth} />
            <Field label="Sex" value={client.sex} />
            {client.address && <Field label="Address" value={client.address} />}
            {client.phoneNumber && <Field label="Phone" value={client.phoneNumber} />}
          </div>
        </div>
      )}
    </div>
  );
}

function Field({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="space-y-0.5">
      <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">{label}</p>
      <p className={`text-sm text-foreground ${mono ? "font-mono" : ""}`}>{value || "—"}</p>
    </div>
  );
}

function ResultRow({
  client,
  selected,
  onClick,
}: {
  client: MaskedClient;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`w-full rounded-2xl border p-4 text-left transition-colors ${
        selected
          ? "border-impilo-300 bg-primary-soft"
          : "border-border bg-card hover:border-border hover:bg-background"
      }`}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-3">
          <div className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full bg-neutral-100">
            <User className="h-4 w-4 text-muted-foreground" />
          </div>
          <div className="space-y-0.5">
            <p className="font-mono text-sm font-medium text-foreground">{client.healthId}</p>
            <p className="text-xs text-muted-foreground">
              {client.maskedName} · {maskYear(client.maskedDob)} · {client.maskedSex}
            </p>
          </div>
        </div>
        {client.matchScore !== undefined && (
          <span className="rounded-xl bg-neutral-100 px-2.5 py-0.5 text-xs font-medium text-muted-foreground">
            Score {client.matchScore}
          </span>
        )}
      </div>
    </button>
  );
}

export default function VitoInternalSearchPage() {
  const [query, setQuery] = useState("");
  const [selectedHealthId, setSelectedHealthId] = useState<string | undefined>(undefined);

  const search = useInternalClientSearch();
  const results: MaskedClient[] = search.data?.data?.items ?? [];

  function handleSearch() {
    const q = query.trim();
    if (!q) return;
    setSelectedHealthId(undefined);
    search.mutate({ query: q });
  }

  function handleSelectRow(healthId: string) {
    setSelectedHealthId((prev) => (prev === healthId ? undefined : healthId));
  }

  return (
    <AppLayout>
      <PageShell
        title="Internal Client Search"
        subtitle="Privacy-preserving staff search — results are masked; click a row to load the full record"
        icon={<Search className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <div className="flex flex-wrap gap-3">
            <Link
              href="/operations/vito"
              className="text-sm text-muted-foreground underline-offset-2 hover:text-foreground hover:underline"
            >
              ← Identity operations
            </Link>
          </div>

          <div className="rounded-2xl border border-border bg-card p-5 space-y-4">
            <div className="flex items-center gap-2">
              <Search className="h-4 w-4 text-muted-foreground" />
              <h2 className="text-sm font-semibold text-foreground">Search</h2>
            </div>
            <div className="flex gap-2">
              <input
                className="flex-1 rounded-xl border border-border px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                placeholder="Name, Health ID, national ID, or partial match…"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && handleSearch()}
              />
              <button
                type="button"
                disabled={search.isPending || !query.trim()}
                onClick={handleSearch}
                className="inline-flex items-center gap-1.5 rounded-xl bg-primary-hover px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
              >
                <Search className="h-4 w-4" />
                {search.isPending ? "Searching…" : "Search"}
              </button>
            </div>

            {search.isError && (
              <div className="flex items-center gap-2 rounded-2xl bg-warning-soft p-4 text-sm text-warning-foreground">
                <AlertTriangle className="h-4 w-4 flex-shrink-0" />
                Search failed. Verify that the internal search service is available.
              </div>
            )}
          </div>

          {search.isSuccess && (
            <div className="rounded-2xl border border-border bg-card p-5 space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-semibold text-foreground">
                  Results
                  {results.length > 0 && (
                    <span className="ml-2 rounded bg-neutral-100 px-2 py-0.5 text-xs font-normal text-muted-foreground">
                      {results.length}
                    </span>
                  )}
                </h2>
                {selectedHealthId && (
                  <p className="text-xs text-muted-foreground">
                    Showing full record for{" "}
                    <span className="font-mono font-medium text-foreground">{selectedHealthId}</span>
                  </p>
                )}
              </div>

              {results.length === 0 ? (
                <div className="rounded-2xl bg-background p-4 text-sm text-muted-foreground">
                  No clients matched your search. Try a broader query.
                </div>
              ) : (
                <div className="space-y-2">
                  {results.map((client) => (
                    <ResultRow
                      key={client.healthId}
                      client={client}
                      selected={selectedHealthId === client.healthId}
                      onClick={() => handleSelectRow(client.healthId)}
                    />
                  ))}
                </div>
              )}
            </div>
          )}

          {selectedHealthId && (
            <DetailPanel
              healthId={selectedHealthId}
              onClose={() => setSelectedHealthId(undefined)}
            />
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
