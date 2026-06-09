"use client";
import { JsonApiDataTable } from "@/components/common/JsonApiDataTable";
import { REGISTRY_ENTITY_COLUMNS } from "@/lib/json-api/generic-table-columns";
import Link from "next/link";
import { useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { type ApiResponse } from "@/lib/api-client";
import {
  useRegistryMode,
  usePendingProvisionalIds,
  usePendingRegistryDedupCases,
  useIssueProvisionalId,
  useReconcileProvisionalId,
  useResolveRegistryDedupCase,
  useOpenCrMatch,
  type ProvisionalId,
} from "@/hooks/queries/useVitoRegistryAdmin";
import { Activity, Database, GitMerge, Search } from "lucide-react";

function ModeBadge({ mode }: { mode: string }) {
  const styles: Record<string, string> = {
    ONLINE: "bg-emerald-50 text-emerald-800 border-emerald-200",
    OFFLINE: "bg-amber-50 text-amber-800 border-amber-200",
    MAINTENANCE: "bg-rose-50 text-rose-800 border-rose-200",
  };
  return (
    <span
      className={`inline-flex items-center rounded-full border px-3 py-1 text-sm font-semibold ${styles[mode] ?? "bg-gray-50 text-gray-700 border-gray-200"}`}
    >
      {mode}
    </span>
  );
}

function IssueProvisionalForm({ onDone }: { onDone: () => void }) {
  const issue = useIssueProvisionalId();
  const [facilityId, setFacilityId] = useState("");
  const [givenName, setGivenName] = useState("");
  const [familyName, setFamilyName] = useState("");
  const [dateOfBirth, setDateOfBirth] = useState("");
  const [sex, setSex] = useState("UNKNOWN");

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    issue.mutate(
      {
        facilityId,
        demographics: { givenName, familyName, dateOfBirth, sex },
      },
      {
        onSuccess: () => {
          setFacilityId("");
          setGivenName("");
          setFamilyName("");
          setDateOfBirth("");
          setSex("UNKNOWN");
          onDone();
        },
      }
    );
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-3 rounded-2xl border border-gray-200 bg-white p-5">
      <h3 className="text-sm font-semibold text-gray-900">Issue provisional ID</h3>
      <div className="grid gap-3 sm:grid-cols-2">
        <label className="flex flex-col gap-1 text-xs text-gray-500 sm:col-span-2">
          Facility ID
          <input
            required
            className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
            value={facilityId}
            onChange={(e) => setFacilityId(e.target.value)}
            placeholder="FAC-XXXX"
          />
        </label>
        <label className="flex flex-col gap-1 text-xs text-gray-500">
          Given name
          <input
            required
            className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
            value={givenName}
            onChange={(e) => setGivenName(e.target.value)}
          />
        </label>
        <label className="flex flex-col gap-1 text-xs text-gray-500">
          Family name
          <input
            required
            className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
            value={familyName}
            onChange={(e) => setFamilyName(e.target.value)}
          />
        </label>
        <label className="flex flex-col gap-1 text-xs text-gray-500">
          Date of birth
          <input
            required
            type="date"
            className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
            value={dateOfBirth}
            onChange={(e) => setDateOfBirth(e.target.value)}
          />
        </label>
        <label className="flex flex-col gap-1 text-xs text-gray-500">
          Sex
          <select
            className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
            value={sex}
            onChange={(e) => setSex(e.target.value)}
          >
            {["MALE", "FEMALE", "UNKNOWN"].map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
      </div>
      <button
        type="submit"
        disabled={issue.isPending}
        className="inline-flex items-center gap-2 rounded-xl bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
      >
        {issue.isPending ? "Issuing…" : "Issue"}
      </button>
      {issue.isError && (
        <p className="text-sm text-red-600">Failed to issue provisional ID. Check service connectivity.</p>
      )}
    </form>
  );
}

function ReconcileDialog({
  provisional,
  onClose,
}: {
  provisional: ProvisionalId;
  onClose: () => void;
}) {
  const reconcile = useReconcileProvisionalId();
  const [healthId, setHealthId] = useState("");

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    reconcile.mutate(
      { ref: provisional.reference, healthId },
      { onSuccess: onClose }
    );
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <form
        onSubmit={handleSubmit}
        className="w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl space-y-4"
      >
        <h3 className="font-semibold text-gray-900">
          Reconcile <span className="font-mono text-impilo-700">{provisional.reference}</span>
        </h3>
        <p className="text-sm text-gray-500">
          Enter the permanent Health ID to link this provisional record to.
        </p>
        <label className="flex flex-col gap-1 text-xs text-gray-500">
          Permanent Health ID
          <input
            required
            className="rounded-lg border border-gray-300 px-2 py-1.5 text-sm"
            value={healthId}
            onChange={(e) => setHealthId(e.target.value)}
            placeholder="HID-XXXXXXXX"
          />
        </label>
        <div className="flex gap-2">
          <button
            type="submit"
            disabled={reconcile.isPending}
            className="flex-1 rounded-xl bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
          >
            {reconcile.isPending ? "Reconciling…" : "Reconcile"}
          </button>
          <button
            type="button"
            onClick={onClose}
            className="flex-1 rounded-xl border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 hover:border-gray-400"
          >
            Cancel
          </button>
        </div>
        {reconcile.isError && (
          <p className="text-sm text-red-600">Reconciliation failed. Try again.</p>
        )}
      </form>
    </div>
  );
}

function OpenCrPanel() {
  const match = useOpenCrMatch();
  const [query, setQuery] = useState("");
  const [result, setResult] = useState<ApiResponse<Record<string, unknown>> | null>(null);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    let parsed: Record<string, unknown>;
    try {
      parsed = JSON.parse(query) as Record<string, unknown>;
    } catch {
      return;
    }
    match.mutate(parsed, {
      onSuccess: (data) => setResult(data),
    });
  }

  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
      <div className="flex items-center gap-2">
        <Search className="h-4 w-4 text-impilo-600" />
        <h3 className="text-sm font-semibold text-gray-900">OpenCR match trigger</h3>
      </div>
      <form onSubmit={handleSubmit} className="space-y-3">
        <label className="flex flex-col gap-1 text-xs text-gray-500">
          Match payload (JSON)
          <textarea
            required
            rows={4}
            className="rounded-lg border border-gray-300 px-2 py-1.5 font-mono text-xs"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder='{"givenName":"…","familyName":"…","dateOfBirth":"…"}'
          />
        </label>
        <button
          type="submit"
          disabled={match.isPending}
          className="inline-flex items-center gap-2 rounded-xl bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
        >
          {match.isPending ? "Matching…" : "Run match"}
        </button>
        {match.isError && (
          <p className="text-sm text-red-600">Match request failed. Verify BFF and OpenCR connectivity.</p>
        )}
      </form>
      {result != null && (
        <JsonApiDataTable data={result} columns={REGISTRY_ENTITY_COLUMNS} emptyTitle="No result fields" />
      )}
    </div>
  );
}

export default function RegistryAdminPage() {
  const modeQuery = useRegistryMode();
  const provisionalQuery = usePendingProvisionalIds();
  const dedupQuery = usePendingRegistryDedupCases();
  const resolveCase = useResolveRegistryDedupCase();

  const [showIssueForm, setShowIssueForm] = useState(false);
  const [reconcileTarget, setReconcileTarget] = useState<ProvisionalId | null>(null);

  const modeData = modeQuery.data?.data;
  const provisionalList = provisionalQuery.data?.data?.items ?? [];
  const dedupList = dedupQuery.data?.data?.items ?? [];

  return (
    <AppLayout>
      <PageShell
        title="Registry administration"
        subtitle="Operational mode, provisional ID management, registry deduplication, and OpenCR match controls"
        icon={<Database className="h-6 w-6" />}
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

          {/* Registry mode */}
          <div className="rounded-2xl border border-gray-200 bg-white p-5">
            <div className="flex items-center gap-2 mb-4">
              <Activity className="h-4 w-4 text-impilo-600" />
              <h2 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">
                Registry mode
              </h2>
            </div>
            {modeQuery.isPending && (
              <p className="text-sm text-gray-400">Loading…</p>
            )}
            {modeQuery.isError && (
              <p className="text-sm text-red-600">Unable to fetch registry mode.</p>
            )}
            {modeData && (
              <div className="flex flex-wrap items-center gap-4">
                <ModeBadge mode={modeData.mode} />
                <span className="text-xs text-gray-400">
                  Updated {new Date(modeData.updatedAt).toLocaleString()} by {modeData.updatedBy}
                </span>
              </div>
            )}
          </div>

          {/* Provisional IDs */}
          <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <GitMerge className="h-4 w-4 text-amber-600" />
                <h2 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">
                  Pending provisional IDs
                </h2>
                {!provisionalQuery.isPending && (
                  <span className="rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-700">
                    {provisionalList.length}
                  </span>
                )}
              </div>
              <button
                type="button"
                onClick={() => setShowIssueForm((v) => !v)}
                className="inline-flex items-center gap-2 rounded-xl border border-impilo-200 bg-impilo-50 px-3 py-1.5 text-sm font-medium text-impilo-900 hover:border-impilo-300"
              >
                {showIssueForm ? "Cancel" : "Issue provisional ID"}
              </button>
            </div>

            {showIssueForm && (
              <IssueProvisionalForm onDone={() => setShowIssueForm(false)} />
            )}

            {provisionalQuery.isPending && (
              <p className="text-sm text-gray-400">Loading…</p>
            )}
            {!provisionalQuery.isPending && provisionalList.length === 0 && (
              <div className="rounded-2xl bg-gray-50 p-4 text-sm text-gray-500">
                No provisional IDs are pending reconciliation.
              </div>
            )}
            {provisionalList.length > 0 && (
              <div className="overflow-x-auto">
                <table className="min-w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-100 text-left text-xs text-gray-400">
                      <th className="pb-2 font-medium">Reference</th>
                      <th className="pb-2 font-medium">Name</th>
                      <th className="pb-2 font-medium">DOB</th>
                      <th className="pb-2 font-medium">Status</th>
                      <th className="pb-2 font-medium">Facility</th>
                      <th className="pb-2 font-medium" />
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-50">
                    {provisionalList.map((p) => (
                      <tr key={p.reference} className="py-2">
                        <td className="py-2 pr-4 font-mono text-xs text-gray-700">{p.reference}</td>
                        <td className="py-2 pr-4 text-gray-900">
                          {p.demographics.givenName} {p.demographics.familyName}
                        </td>
                        <td className="py-2 pr-4 text-gray-500">{p.demographics.dateOfBirth}</td>
                        <td className="py-2 pr-4">
                          <span
                            className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                              p.status === "RECONCILED"
                                ? "bg-emerald-50 text-emerald-700"
                                : p.status === "EXPIRED"
                                  ? "bg-gray-100 text-gray-500"
                                  : "bg-amber-50 text-amber-700"
                            }`}
                          >
                            {p.status}
                          </span>
                        </td>
                        <td className="py-2 pr-4 text-xs text-gray-400">{p.facilityId}</td>
                        <td className="py-2">
                          {p.status === "PENDING" && (
                            <button
                              type="button"
                              onClick={() => setReconcileTarget(p)}
                              className="rounded-lg border border-gray-300 px-3 py-1 text-xs font-medium text-gray-700 hover:border-gray-400"
                            >
                              Reconcile
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Registry dedup cases */}
          <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
            <div className="flex items-center gap-2">
              <GitMerge className="h-4 w-4 text-rose-500" />
              <h2 className="text-sm font-semibold uppercase tracking-[0.18em] text-gray-500">
                Registry deduplication cases
              </h2>
              {!dedupQuery.isPending && (
                <span className="rounded-full bg-rose-50 px-2 py-0.5 text-xs font-medium text-rose-700">
                  {dedupList.length}
                </span>
              )}
            </div>

            {dedupQuery.isPending && (
              <p className="text-sm text-gray-400">Loading…</p>
            )}
            {!dedupQuery.isPending && dedupList.length === 0 && (
              <div className="rounded-2xl bg-gray-50 p-4 text-sm text-gray-500">
                No registry deduplication cases are open.
              </div>
            )}
            {dedupList.length > 0 && (
              <div className="overflow-x-auto">
                <table className="min-w-full text-sm">
                  <thead>
                    <tr className="border-b border-gray-100 text-left text-xs text-gray-400">
                      <th className="pb-2 font-medium">Case ID</th>
                      <th className="pb-2 font-medium">Source HID</th>
                      <th className="pb-2 font-medium">Target HID</th>
                      <th className="pb-2 font-medium">Score</th>
                      <th className="pb-2 font-medium">Status</th>
                      <th className="pb-2 font-medium">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-50">
                    {dedupList.map((c) => (
                      <tr key={c.caseId} className="py-2">
                        <td className="py-2 pr-4 font-mono text-xs text-gray-700">{c.caseId.slice(0, 8)}</td>
                        <td className="py-2 pr-4 font-mono text-xs text-gray-700">{c.sourceHealthId.slice(0, 10)}</td>
                        <td className="py-2 pr-4 font-mono text-xs text-gray-700">{c.targetHealthId.slice(0, 10)}</td>
                        <td className="py-2 pr-4 text-gray-700">{c.score.toFixed(2)}</td>
                        <td className="py-2 pr-4">
                          <span
                            className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                              c.status === "RESOLVED"
                                ? "bg-emerald-50 text-emerald-700"
                                : c.status === "IGNORED"
                                  ? "bg-gray-100 text-gray-500"
                                  : "bg-rose-50 text-rose-700"
                            }`}
                          >
                            {c.status}
                          </span>
                        </td>
                        <td className="py-2">
                          {c.status === "OPEN" && (
                            <div className="flex gap-2">
                              <button
                                type="button"
                                disabled={resolveCase.isPending}
                                onClick={() =>
                                  resolveCase.mutate({
                                    caseId: c.caseId,
                                    resolution: "MERGE",
                                  })
                                }
                                className="rounded-lg bg-rose-600 px-3 py-1 text-xs font-medium text-white hover:bg-rose-700 disabled:opacity-50"
                              >
                                Merge
                              </button>
                              <button
                                type="button"
                                disabled={resolveCase.isPending}
                                onClick={() =>
                                  resolveCase.mutate({
                                    caseId: c.caseId,
                                    resolution: "IGNORE",
                                  })
                                }
                                className="rounded-lg border border-gray-300 px-3 py-1 text-xs font-medium text-gray-700 hover:border-gray-400 disabled:opacity-50"
                              >
                                Ignore
                              </button>
                            </div>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* OpenCR match */}
          <OpenCrPanel />
        </div>

        {reconcileTarget && (
          <ReconcileDialog
            provisional={reconcileTarget}
            onClose={() => setReconcileTarget(null)}
          />
        )}
      </PageShell>
    </AppLayout>
  );
}
