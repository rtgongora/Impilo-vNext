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
    ACTIVE: "bg-emerald-50 text-emerald-700 border-emerald-200",
    INACTIVE: "bg-gray-100 text-gray-600 border-gray-200",
    DECEASED: "bg-rose-50 text-rose-700 border-rose-200",
    SUSPENDED: "bg-amber-50 text-amber-700 border-amber-200",
  };
  const cls = map[status] ?? "bg-gray-50 text-gray-600 border-gray-200";
  return (
    <span className={`inline-block rounded-full border px-2.5 py-0.5 text-xs font-medium ${cls}`}>
      {status}
    </span>
  );
}

function assuranceBadge(level: string) {
  const map: Record<string, string> = {
    HIGH: "bg-emerald-50 text-emerald-700",
    MEDIUM: "bg-sky-50 text-sky-700",
    LOW: "bg-amber-50 text-amber-700",
    NONE: "bg-gray-50 text-gray-500",
  };
  const cls = map[level] ?? "bg-gray-50 text-gray-500";
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
    <div className="rounded-2xl border border-impilo-200 bg-impilo-50 p-5 space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <User className="h-4 w-4 text-impilo-600" />
          <h2 className="text-sm font-semibold text-gray-900">Full Client Record</h2>
          <span className="rounded bg-impilo-100 px-2 py-0.5 text-xs text-impilo-700">{healthId}</span>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="rounded-lg p-1 text-gray-400 hover:bg-white hover:text-gray-700"
          aria-label="Close detail panel"
        >
          <X className="h-4 w-4" />
        </button>
      </div>

      {query.isLoading && (
        <p className="text-sm text-gray-400">Loading full record…</p>
      )}

      {query.isError && (
        <div className="flex items-center gap-2 rounded-2xl bg-amber-50 p-4 text-sm text-amber-700">
          <AlertTriangle className="h-4 w-4 flex-shrink-0" />
          Unable to load this client record. Access may require a higher assurance level.
        </div>
      )}

      {client && (
        <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
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
      <p className="text-xs font-medium uppercase tracking-wider text-gray-400">{label}</p>
      <p className={`text-sm text-gray-900 ${mono ? "font-mono" : ""}`}>{value || "—"}</p>
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
          ? "border-impilo-300 bg-impilo-50"
          : "border-gray-200 bg-white hover:border-gray-300 hover:bg-gray-50"
      }`}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-3">
          <div className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-full bg-gray-100">
            <User className="h-4 w-4 text-gray-500" />
          </div>
          <div className="space-y-0.5">
            <p className="font-mono text-sm font-medium text-gray-900">{client.healthId}</p>
            <p className="text-xs text-gray-500">
              {client.maskedName} · {maskYear(client.maskedDob)} · {client.maskedSex}
            </p>
          </div>
        </div>
        {client.matchScore !== undefined && (
          <span className="rounded-xl bg-gray-100 px-2.5 py-0.5 text-xs font-medium text-gray-600">
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
              className="text-sm text-gray-600 underline-offset-2 hover:text-gray-900 hover:underline"
            >
              ← Identity operations
            </Link>
          </div>

          <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
            <div className="flex items-center gap-2">
              <Search className="h-4 w-4 text-gray-500" />
              <h2 className="text-sm font-semibold text-gray-900">Search</h2>
            </div>
            <div className="flex gap-2">
              <input
                className="flex-1 rounded-xl border border-gray-300 px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                placeholder="Name, Health ID, national ID, or partial match…"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && handleSearch()}
              />
              <button
                type="button"
                disabled={search.isPending || !query.trim()}
                onClick={handleSearch}
                className="inline-flex items-center gap-1.5 rounded-xl bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
              >
                <Search className="h-4 w-4" />
                {search.isPending ? "Searching…" : "Search"}
              </button>
            </div>

            {search.isError && (
              <div className="flex items-center gap-2 rounded-2xl bg-amber-50 p-4 text-sm text-amber-700">
                <AlertTriangle className="h-4 w-4 flex-shrink-0" />
                Search failed. Verify that the internal search service is available.
              </div>
            )}
          </div>

          {search.isSuccess && (
            <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-sm font-semibold text-gray-900">
                  Results
                  {results.length > 0 && (
                    <span className="ml-2 rounded bg-gray-100 px-2 py-0.5 text-xs font-normal text-gray-500">
                      {results.length}
                    </span>
                  )}
                </h2>
                {selectedHealthId && (
                  <p className="text-xs text-gray-400">
                    Showing full record for{" "}
                    <span className="font-mono font-medium text-gray-700">{selectedHealthId}</span>
                  </p>
                )}
              </div>

              {results.length === 0 ? (
                <div className="rounded-2xl bg-gray-50 p-4 text-sm text-gray-500">
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
