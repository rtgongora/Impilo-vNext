"use client";

import Link from "next/link";
import { useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useIssuanceQueue,
  useSubmitIssuance,
  type IssuanceState,
  type IssuanceType,
  type IssuanceChannel,
} from "@/hooks/queries/useVitoIssuance";
import { FileText, ChevronLeft, ChevronRight, Plus, X } from "lucide-react";

const STATES: Array<IssuanceState | "ALL"> = [
  "ALL",
  "SUBMITTED",
  "PROOFING",
  "APPROVED",
  "ISSUED",
  "DELIVERED",
  "REJECTED",
];

const STATE_STYLES: Record<IssuanceState, string> = {
  SUBMITTED: "bg-blue-50 text-blue-700 border-blue-100",
  PROOFING: "bg-amber-50 text-amber-700 border-amber-100",
  APPROVED: "bg-emerald-50 text-emerald-700 border-emerald-100",
  ISSUED: "bg-sky-50 text-sky-700 border-sky-100",
  DELIVERED: "bg-green-50 text-green-700 border-green-100",
  REJECTED: "bg-red-50 text-red-700 border-red-100",
};

const TYPE_LABELS: Record<string, string> = {
  FIRST_ISSUE: "First issue",
  REPLACEMENT: "Replacement",
  RENEWAL: "Renewal",
};

const CHANNEL_LABELS: Record<string, string> = {
  ONLINE: "Online",
  IN_PERSON: "In person",
};

function formatDate(iso: string) {
  return new Date(iso).toLocaleDateString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

const ISSUANCE_TYPES: IssuanceType[] = ["FIRST_ISSUE", "REPLACEMENT", "RENEWAL"];
const ISSUANCE_CHANNELS: IssuanceChannel[] = ["ONLINE", "IN_PERSON"];

function NewRequestSlideOver({ onClose }: { onClose: () => void }) {
  const submitIssuance = useSubmitIssuance();
  const [healthId, setHealthId] = useState("");
  const [type, setType] = useState<IssuanceType>("FIRST_ISSUE");
  const [channel, setChannel] = useState<IssuanceChannel>("IN_PERSON");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!healthId.trim()) {
      setError("Health ID is required.");
      return;
    }
    try {
      await submitIssuance.mutateAsync({
        healthId: healthId.trim(),
        type,
        channel,
        notes: notes.trim() || undefined,
      });
      onClose();
    } catch {
      setError("Failed to submit issuance request. Please try again.");
    }
  }

  return (
    <>
      <div
        className="fixed inset-0 z-40 bg-black/25"
        aria-hidden="true"
        onClick={onClose}
      />
      <div className="fixed inset-y-0 right-0 z-50 flex w-full max-w-md flex-col bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-gray-200 px-6 py-4">
          <h2 className="text-base font-semibold text-gray-900">New issuance request</h2>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1.5 text-gray-400 hover:bg-gray-100 hover:text-gray-600"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-1 flex-col overflow-y-auto">
          <div className="flex-1 space-y-5 px-6 py-6">
            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-gray-700" htmlFor="healthId">
                Health ID
              </label>
              <input
                id="healthId"
                type="text"
                required
                className="w-full rounded-xl border border-gray-300 px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                placeholder="e.g. b0000000-0000-4000-8000-000000000001"
                value={healthId}
                onChange={(e) => setHealthId(e.target.value)}
              />
            </div>

            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-gray-700" htmlFor="type">
                Request type
              </label>
              <select
                id="type"
                value={type}
                onChange={(e) => setType(e.target.value as IssuanceType)}
                className="w-full rounded-xl border border-gray-300 px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
              >
                {ISSUANCE_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {TYPE_LABELS[t] ?? t}
                  </option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-gray-700" htmlFor="channel">
                Channel
              </label>
              <select
                id="channel"
                value={channel}
                onChange={(e) => setChannel(e.target.value as IssuanceChannel)}
                className="w-full rounded-xl border border-gray-300 px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
              >
                {ISSUANCE_CHANNELS.map((c) => (
                  <option key={c} value={c}>
                    {CHANNEL_LABELS[c] ?? c}
                  </option>
                ))}
              </select>
            </div>

            <div className="space-y-1.5">
              <label className="block text-sm font-medium text-gray-700" htmlFor="notes">
                Notes <span className="font-normal text-gray-400">(optional)</span>
              </label>
              <textarea
                id="notes"
                rows={3}
                className="w-full rounded-xl border border-gray-300 px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
                placeholder="Additional context for this request…"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
              />
            </div>

            {error && (
              <p className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>
            )}
          </div>

          <div className="border-t border-gray-200 px-6 py-4">
            <div className="flex gap-3">
              <button
                type="button"
                onClick={onClose}
                className="flex-1 rounded-xl border border-gray-300 px-4 py-2.5 text-sm font-medium text-gray-700 hover:border-gray-400 hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={submitIssuance.isPending}
                className="flex-1 rounded-xl bg-impilo-600 px-4 py-2.5 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
              >
                {submitIssuance.isPending ? "Submitting…" : "Submit request"}
              </button>
            </div>
          </div>
        </form>
      </div>
    </>
  );
}

export default function IssuanceQueuePage() {
  const [selectedState, setSelectedState] = useState<IssuanceState | "ALL">("ALL");
  const [page, setPage] = useState(0);
  const [showNewRequest, setShowNewRequest] = useState(false);
  const PAGE_SIZE = 20;

  const stateFilter = selectedState === "ALL" ? undefined : selectedState;
  const queue = useIssuanceQueue(stateFilter, page, PAGE_SIZE);
  const items = queue.data?.data?.items ?? [];
  const hasNextPage = items.length === PAGE_SIZE;

  return (
    <AppLayout>
      <PageShell
        title="Issuance queue"
        subtitle="Identity document issuance workflow — submit, proof, approve, issue, and deliver"
        icon={<FileText className="h-6 w-6" />}
      >
        {showNewRequest && (
          <NewRequestSlideOver onClose={() => setShowNewRequest(false)} />
        )}

        <div className="space-y-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <Link
              href="/operations/vito"
              className="text-sm text-gray-600 hover:text-gray-900 underline-offset-2 hover:underline"
            >
              ← Identity operations
            </Link>
            <button
              type="button"
              onClick={() => setShowNewRequest(true)}
              className="inline-flex items-center gap-1.5 rounded-xl bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700"
            >
              <Plus className="h-4 w-4" />
              New request
            </button>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            {STATES.map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => {
                  setSelectedState(s);
                  setPage(0);
                }}
                className={`rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
                  selectedState === s
                    ? "border-impilo-300 bg-impilo-50 text-impilo-900"
                    : "border-gray-200 bg-white text-gray-600 hover:border-gray-300 hover:text-gray-900"
                }`}
              >
                {s === "ALL" ? "All states" : s.charAt(0) + s.slice(1).toLowerCase()}
              </button>
            ))}
          </div>

          <div className="rounded-2xl border border-gray-200 bg-white">
            {queue.isLoading ? (
              <div className="p-8 text-center text-sm text-gray-500">Loading queue…</div>
            ) : queue.isError ? (
              <div className="p-8 text-center text-sm text-red-600">
                Failed to load issuance queue. Check that the Experience BFF is reachable.
              </div>
            ) : items.length === 0 ? (
              <div className="p-8 text-center text-sm text-gray-500">
                No issuance requests match the selected filter.
              </div>
            ) : (
              <div className="divide-y divide-gray-100">
                {items.map((item) => (
                  <div
                    key={item.id}
                    className="flex flex-wrap items-center justify-between gap-4 px-5 py-4 hover:bg-gray-50"
                  >
                    <Link href={`/operations/vito/issuance/${item.id}`} className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="font-medium text-gray-900 truncate">{item.healthId}</p>
                        <span
                          className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium ${
                            STATE_STYLES[item.state]
                          }`}
                        >
                          {item.state.charAt(0) + item.state.slice(1).toLowerCase()}
                        </span>
                      </div>
                      <p className="mt-1 text-xs text-gray-500">
                        {TYPE_LABELS[item.type] ?? item.type} &bull; {CHANNEL_LABELS[item.channel] ?? item.channel} &bull;{" "}
                        Requested {formatDate(item.requestedAt)}
                      </p>
                      {item.notes && (
                        <p className="mt-1 text-xs text-gray-400 truncate">{item.notes}</p>
                      )}
                    </Link>
                    <div className="flex shrink-0 flex-wrap items-center gap-2">
                      {(item.state === "APPROVED" || item.state === "ISSUED") && (
                        <Link
                          href="/operations/vito/print"
                          className="rounded-lg border border-gray-200 px-2.5 py-1 text-xs font-medium text-gray-700 hover:border-gray-300"
                        >
                          Print
                        </Link>
                      )}
                      {item.state === "ISSUED" && (
                        <Link
                          href="/operations/vito/cards/pickup"
                          className="rounded-lg border border-sky-200 bg-sky-50 px-2.5 py-1 text-xs font-medium text-sky-800 hover:border-sky-300"
                        >
                          Pickup
                        </Link>
                      )}
                      <Link
                        href={`/operations/vito/issuance/${item.id}`}
                        className="text-xs text-gray-400 hover:text-gray-700"
                      >
                        View →
                      </Link>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          {(page > 0 || hasNextPage) && (
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
              <span className="text-sm text-gray-500">Page {page + 1}</span>
              <button
                type="button"
                disabled={!hasNextPage}
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
