"use client";

import Link from "next/link";
import { useState } from "react";
import { Share2, Search, XCircle, Clock, User, FileText } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  usePatientShares,
  useShareContributions,
  useCreatePatientShare,
  useRevokePatientShare,
  type PatientShare,
  type ShareContribution,
} from "@/hooks/queries/useVitoPatientShares";

const SHARE_PURPOSES = ["TREATMENT", "RESEARCH", "PAYMENT", "EMERGENCY"] as const;
type SharePurpose = (typeof SHARE_PURPOSES)[number];

function shareBadge(status: PatientShare["status"]) {
  const map: Record<PatientShare["status"], string> = {
    PENDING: "bg-amber-50 text-amber-800 border-amber-100",
    ACTIVE: "bg-emerald-50 text-emerald-800 border-emerald-100",
    REVOKED: "bg-red-50 text-red-700 border-red-100",
    EXPIRED: "bg-gray-100 text-gray-600 border-gray-200",
  };
  return map[status] ?? "bg-gray-100 text-gray-600 border-gray-200";
}

function fmt(value: string | undefined) {
  if (!value) return "—";
  return new Date(value).toLocaleDateString("en-ZW", { dateStyle: "medium" });
}

function fmtTime(value: string) {
  return new Date(value).toLocaleString("en-ZW", { dateStyle: "medium", timeStyle: "short" });
}

function ShareRow({
  share,
  onRevoke,
  isRevoking,
  onSelectShare,
  isSelected,
}: {
  share: PatientShare;
  onRevoke: (healthId: string, shareId: string) => void;
  isRevoking: boolean;
  onSelectShare: (shareId: string) => void;
  isSelected: boolean;
}) {
  return (
    <div
      className={`rounded-2xl border p-4 space-y-2 transition-colors ${
        isSelected ? "border-impilo-300 bg-impilo-50" : "border-gray-200 bg-white"
      }`}
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="space-y-0.5">
          <div className="flex items-center gap-2">
            <span
              className={`rounded-full border px-2.5 py-0.5 text-xs font-medium ${shareBadge(share.status)}`}
            >
              {share.status}
            </span>
            <span className="text-xs text-gray-400 font-mono">{share.id}</span>
          </div>
          <p className="text-sm font-medium text-gray-900">
            {share.recipientName}
            <span className="ml-1.5 text-xs text-gray-400 font-mono">({share.recipientId})</span>
          </p>
          <p className="text-xs text-gray-500">
            Purpose: <span className="font-medium text-gray-700">{share.purpose}</span>
            {" · "}Created {fmt(share.createdAt)}
            {" · "}Expires {fmt(share.expiresAt)}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => onSelectShare(share.id)}
            className={`rounded-xl border px-3 py-1.5 text-xs font-medium transition-colors ${
              isSelected
                ? "border-impilo-300 bg-impilo-100 text-impilo-900"
                : "border-gray-300 bg-white text-gray-700 hover:border-gray-400"
            }`}
          >
            <FileText className="inline h-3 w-3 mr-1" />
            {isSelected ? "Viewing" : "Contributions"}
          </button>
          {(share.status === "ACTIVE" || share.status === "PENDING") && (
            <button
              type="button"
              disabled={isRevoking}
              onClick={() => onRevoke(share.healthId, share.id)}
              className="inline-flex items-center gap-1.5 rounded-xl border border-red-200 bg-red-50 px-3 py-1.5 text-xs font-medium text-red-700 hover:bg-red-100 disabled:opacity-50"
            >
              <XCircle className="h-3.5 w-3.5" />
              Revoke
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

function ContributionItem({ contribution }: { contribution: ShareContribution }) {
  return (
    <div className="flex gap-3">
      <div className="flex flex-col items-center">
        <div className="flex h-7 w-7 items-center justify-center rounded-full bg-impilo-100 text-impilo-700">
          <User className="h-3.5 w-3.5" />
        </div>
        <div className="mt-1 flex-1 w-px bg-gray-100" />
      </div>
      <div className="pb-4 min-w-0 flex-1">
        <div className="flex flex-wrap items-baseline gap-2">
          <p className="text-sm font-medium text-gray-900">{contribution.contributorName}</p>
          <span className="rounded-md bg-gray-100 px-1.5 py-0.5 text-xs text-gray-600">
            {contribution.type}
          </span>
          <span className="ml-auto text-xs text-gray-400 flex-shrink-0">
            <Clock className="inline h-3 w-3 mr-0.5" />
            {fmtTime(contribution.timestamp)}
          </span>
        </div>
        <p className="mt-0.5 text-sm text-gray-600">{contribution.contentSummary}</p>
        <p className="text-xs text-gray-400 font-mono">{contribution.id}</p>
      </div>
    </div>
  );
}

function SearchPanel() {
  const [input, setInput] = useState("");
  const [healthId, setHealthId] = useState<string | undefined>(undefined);
  const [selectedShareId, setSelectedShareId] = useState<string | undefined>(undefined);

  const shares = usePatientShares(healthId);
  const contributions = useShareContributions(healthId, selectedShareId);
  const revoke = useRevokePatientShare();

  const shareList: PatientShare[] = shares.data?.data ?? [];

  function handleSearch() {
    const trimmed = input.trim();
    if (trimmed) {
      setHealthId(trimmed);
      setSelectedShareId(undefined);
    }
  }

  function handleSelectShare(shareId: string) {
    setSelectedShareId((prev) => (prev === shareId ? undefined : shareId));
  }

  return (
    <div className="space-y-4">
      <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
        <h2 className="text-sm font-semibold text-gray-900">Search shares by Health ID</h2>
        <div className="flex gap-2">
          <input
            className="flex-1 rounded-xl border border-gray-300 px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300 font-mono"
            placeholder="e.g. ZW-0001-2345"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleSearch()}
          />
          <button
            type="button"
            onClick={handleSearch}
            className="inline-flex items-center gap-1.5 rounded-xl bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700"
          >
            <Search className="h-4 w-4" />
            Search
          </button>
        </div>

        {healthId && (
          <>
            <p className="text-xs text-gray-500">
              Showing shares for <span className="font-medium font-mono text-gray-800">{healthId}</span>
            </p>

            {shares.isLoading && (
              <p className="text-sm text-gray-400">Loading shares…</p>
            )}
            {shares.isError && (
              <div className="rounded-2xl border border-red-100 bg-red-50 p-4 text-sm text-red-700">
                Failed to load patient shares. Check the Health ID and service connectivity.
              </div>
            )}
            {!shares.isLoading && !shares.isError && shareList.length === 0 && (
              <div className="rounded-2xl bg-gray-50 p-4 text-sm text-gray-500">
                No patient shares found for this Health ID.
              </div>
            )}

            {shareList.length > 0 && (
              <div className="space-y-3">
                {shareList.map((share) => (
                  <ShareRow
                    key={share.id}
                    share={share}
                    onRevoke={(hid, sid) => revoke.mutate({ healthId: hid, shareRequestId: sid })}
                    isRevoking={revoke.isPending}
                    onSelectShare={handleSelectShare}
                    isSelected={selectedShareId === share.id}
                  />
                ))}
              </div>
            )}
          </>
        )}
      </div>

      {selectedShareId && healthId && (
        <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
          <h2 className="text-sm font-semibold text-gray-900">
            Contributions
            <span className="ml-2 font-mono text-xs font-normal text-gray-400">{selectedShareId}</span>
          </h2>

          {contributions.isLoading && (
            <p className="text-sm text-gray-400">Loading contributions…</p>
          )}
          {contributions.isError && (
            <div className="rounded-2xl border border-red-100 bg-red-50 p-4 text-sm text-red-700">
              Failed to load contributions for this share.
            </div>
          )}
          {!contributions.isLoading && !contributions.isError && (contributions.data?.data ?? []).length === 0 && (
            <div className="rounded-2xl bg-gray-50 p-4 text-sm text-gray-500">
              No contributions recorded for this share yet.
            </div>
          )}

          {(contributions.data?.data ?? []).length > 0 && (
            <div className="mt-2">
              {(contributions.data?.data ?? []).map((c) => (
                <ContributionItem key={c.id} contribution={c} />
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function CreateSharePanel() {
  const createShare = useCreatePatientShare();
  const [form, setForm] = useState<{
    healthId: string;
    purpose: SharePurpose;
    recipientId: string;
    recipientName: string;
    expiresAt: string;
  }>({
    healthId: "",
    purpose: "TREATMENT",
    recipientId: "",
    recipientName: "",
    expiresAt: "",
  });

  const [newShareId, setNewShareId] = useState<string | undefined>(undefined);

  function handleChange(field: keyof typeof form, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
    setNewShareId(undefined);
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const { healthId, ...body } = form;
    if (!healthId.trim()) return;
    createShare.mutate(
      {
        healthId: healthId.trim(),
        body: { ...body, expiresAt: body.expiresAt || undefined },
      },
      {
        onSuccess: (data) => {
          const created = data?.data;
          if (created) setNewShareId(created.id);
          setForm({ healthId: "", purpose: "TREATMENT", recipientId: "", recipientName: "", expiresAt: "" });
        },
      }
    );
  }

  const isDisabled =
    createShare.isPending ||
    !form.healthId.trim() ||
    !form.recipientId.trim() ||
    !form.recipientName.trim();

  return (
    <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
      <h2 className="text-sm font-semibold text-gray-900">Create new share</h2>
      <form onSubmit={handleSubmit} className="space-y-3">
        <label className="flex flex-col gap-1 text-xs text-gray-500">
          Health ID
          <input
            required
            className="rounded-xl border border-gray-300 px-3 py-2 text-sm font-mono focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
            placeholder="e.g. ZW-0001-2345"
            value={form.healthId}
            onChange={(e) => handleChange("healthId", e.target.value)}
          />
        </label>

        <label className="flex flex-col gap-1 text-xs text-gray-500">
          Purpose
          <select
            className="rounded-xl border border-gray-300 px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
            value={form.purpose}
            onChange={(e) => handleChange("purpose", e.target.value as SharePurpose)}
          >
            {SHARE_PURPOSES.map((p) => (
              <option key={p} value={p}>
                {p}
              </option>
            ))}
          </select>
        </label>

        <label className="flex flex-col gap-1 text-xs text-gray-500">
          Recipient ID
          <input
            required
            className="rounded-xl border border-gray-300 px-3 py-2 text-sm font-mono focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
            placeholder="e.g. provider-uuid or facility-uuid"
            value={form.recipientId}
            onChange={(e) => handleChange("recipientId", e.target.value)}
          />
        </label>

        <label className="flex flex-col gap-1 text-xs text-gray-500">
          Recipient name
          <input
            required
            className="rounded-xl border border-gray-300 px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
            placeholder="e.g. Parirenyatwa Group of Hospitals"
            value={form.recipientName}
            onChange={(e) => handleChange("recipientName", e.target.value)}
          />
        </label>

        <label className="flex flex-col gap-1 text-xs text-gray-500">
          Expires at <span className="text-gray-400">(optional)</span>
          <input
            type="datetime-local"
            className="rounded-xl border border-gray-300 px-3 py-2 text-sm focus:border-impilo-400 focus:outline-none focus:ring-1 focus:ring-impilo-300"
            value={form.expiresAt}
            onChange={(e) => handleChange("expiresAt", e.target.value)}
          />
        </label>

        <button
          type="submit"
          disabled={isDisabled}
          className="w-full rounded-xl bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
        >
          {createShare.isPending ? "Creating…" : "Create share"}
        </button>

        {createShare.isError && (
          <p className="text-xs text-red-600">Failed to create share. Verify the Health ID and try again.</p>
        )}

        {newShareId && (
          <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-3">
            <p className="text-xs font-medium text-emerald-800">Share created successfully</p>
            <p className="mt-0.5 font-mono text-xs text-emerald-700">{newShareId}</p>
          </div>
        )}
      </form>
    </div>
  );
}

export default function VitoPatientSharesPage() {
  return (
    <AppLayout>
      <PageShell
        title="Patient Shares"
        subtitle="Search, create, revoke, and review contributions for patient data shares"
        icon={<Share2 className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <div className="flex flex-wrap gap-3">
            <Link
              href="/operations/vito"
              className="text-sm text-gray-600 hover:text-gray-900 underline-offset-2 hover:underline"
            >
              ← Identity operations
            </Link>
          </div>

          <div className="grid gap-6 lg:grid-cols-3">
            <div className="lg:col-span-2">
              <SearchPanel />
            </div>

            <div>
              <CreateSharePanel />
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
