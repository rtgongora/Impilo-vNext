"use client";

import { useState } from "react";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  usePatientShares,
  useRevokePatientShare,
  useCreatePatientShare,
  useShareContributions,
  type PatientShare,
} from "@/hooks/queries/useVitoPatientShares";

const STATUS_COLOURS: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-800",
  PENDING: "bg-yellow-100 text-yellow-800",
  REVOKED: "bg-red-100 text-red-800",
  EXPIRED: "bg-gray-100 text-gray-600",
};

function StatusBadge({ status }: { status: string }) {
  const cls = STATUS_COLOURS[status] ?? "bg-gray-100 text-gray-600";
  return (
    <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${cls}`}>{status}</span>
  );
}

function ContributionsPanel({ healthId, shareId, onClose }: { healthId: string; shareId: string; onClose: () => void }) {
  const { data, isLoading, isError } = useShareContributions(healthId, shareId);
  const contributions = data?.data ?? [];

  return (
    <div className="mt-3 rounded-lg border border-blue-100 bg-blue-50 p-4 text-sm">
      <div className="mb-2 flex items-center justify-between">
        <span className="font-medium text-blue-900">Contributions for share {shareId}</span>
        <button type="button" onClick={onClose} className="text-xs text-blue-700 underline">
          Close
        </button>
      </div>
      {isLoading ? (
        <p className="text-blue-700">Loading…</p>
      ) : isError ? (
        <p className="text-red-700">Failed to load contributions.</p>
      ) : contributions.length === 0 ? (
        <p className="text-gray-600">No contributions yet.</p>
      ) : (
        <ul className="divide-y divide-blue-100">
          {contributions.map((c) => (
            <li key={c.id} className="py-2">
              <span className="font-medium">{c.contributorName}</span>
              <span className="ml-2 text-gray-600">{c.type}</span>
              <p className="mt-0.5 text-xs text-gray-500">{c.contentSummary}</p>
              <p className="mt-0.5 text-xs text-gray-400">{c.timestamp}</p>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function ShareCard({
  share,
  healthId,
  onRevoke,
  revoking,
}: {
  share: PatientShare;
  healthId: string;
  onRevoke: (shareId: string) => void;
  revoking: boolean;
}) {
  const [showContributions, setShowContributions] = useState(false);

  return (
    <li className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
      <div className="flex items-start justify-between gap-4">
        <div className="space-y-1">
          <div className="flex items-center gap-2">
            <span className="font-medium text-gray-900 text-sm">#{share.id}</span>
            <StatusBadge status={share.status} />
          </div>
          <p className="text-sm text-gray-600">
            <span className="font-medium">Purpose:</span> {share.purpose}
          </p>
          <p className="text-sm text-gray-600">
            <span className="font-medium">Recipient:</span> {share.recipientName}
          </p>
          <p className="text-xs text-gray-500">
            Created: {share.createdAt} · Expires: {share.expiresAt}
          </p>
        </div>
        <div className="flex flex-col gap-2 shrink-0">
          {share.status === "ACTIVE" || share.status === "PENDING" ? (
            <button
              type="button"
              disabled={revoking}
              onClick={() => onRevoke(share.id)}
              className="rounded bg-red-600 px-3 py-1 text-xs font-medium text-white disabled:opacity-50 hover:bg-red-700"
            >
              {revoking ? "Revoking…" : "Revoke"}
            </button>
          ) : null}
          <button
            type="button"
            onClick={() => setShowContributions((v) => !v)}
            className="rounded bg-gray-100 px-3 py-1 text-xs font-medium text-gray-700 hover:bg-gray-200"
          >
            {showContributions ? "Hide" : "Contributions"}
          </button>
        </div>
      </div>
      {showContributions ? (
        <ContributionsPanel
          healthId={healthId}
          shareId={share.id}
          onClose={() => setShowContributions(false)}
        />
      ) : null}
    </li>
  );
}

export default function CitizenRecordSharingPage() {
  const user = useAuthStore((s) => s.user);
  const healthId = user?.healthId;

  const [purpose, setPurpose] = useState("LIMITED_CLINICAL_SUMMARY");
  const [recipientId, setRecipientId] = useState("");
  const [expiresAt, setExpiresAt] = useState("");
  const [formError, setFormError] = useState("");
  const [createSuccess, setCreateSuccess] = useState<Record<string, unknown> | null>(null);
  const [revokingId, setRevokingId] = useState<string | null>(null);

  const { data, isLoading, isError, refetch } = usePatientShares(healthId);
  const shares = data?.data ?? [];

  const createShare = useCreatePatientShare();
  const revokeShare = useRevokePatientShare();

  if (!healthId) {
    return (
      <div className="max-w-xl p-6 text-sm text-gray-700">
        Sign in with a profile that has a Health ID to manage record sharing.
      </div>
    );
  }

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault();
    setFormError("");
    setCreateSuccess(null);
    try {
      const res = await createShare.mutateAsync({
        healthId: healthId!,
        body: { purpose, recipientId, expiresAt: expiresAt || undefined },
      });
      setCreateSuccess((res.data as unknown as Record<string, unknown>) ?? {});
      setRecipientId("");
      setExpiresAt("");
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Create failed");
    }
  }

  async function handleRevoke(shareId: string) {
    setRevokingId(shareId);
    try {
      await revokeShare.mutateAsync({ healthId: healthId!, shareRequestId: shareId });
    } catch {
      // error surfaces via query refetch
    } finally {
      setRevokingId(null);
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-8 p-6">
      <div>
        <h1 className="text-xl font-semibold text-gray-900">Share my record</h1>
        <p className="mt-1 text-sm text-gray-600">
          Grant a governed, time-bound share of your health record to a provider or facility. Access is
          revocable and policy-controlled.
        </p>
      </div>

      {/* Create share form */}
      <form
        onSubmit={handleCreate}
        className="space-y-4 rounded-lg border border-gray-200 bg-white p-4 shadow-sm"
      >
        <h2 className="text-base font-medium text-gray-900">Create a share</h2>

        <label className="block text-sm font-medium text-gray-800">
          Scope / purpose
          <select
            className="mt-1 w-full rounded border border-gray-300 px-2 py-1 text-sm"
            value={purpose}
            onChange={(e) => setPurpose(e.target.value)}
          >
            <option value="LIMITED_CLINICAL_SUMMARY">Limited clinical summary</option>
            <option value="ENCOUNTER_CONTEXT">Encounter context</option>
            <option value="REFERRAL_CONTEXT">Referral context</option>
            <option value="DOCUMENT_SET">Document set</option>
          </select>
        </label>

        <label className="block text-sm font-medium text-gray-800">
          Recipient ID
          <input
            type="text"
            required
            className="mt-1 w-full rounded border border-gray-300 px-2 py-1 text-sm"
            value={recipientId}
            onChange={(e) => setRecipientId(e.target.value)}
            placeholder="Provider or facility ID"
          />
        </label>

        <label className="block text-sm font-medium text-gray-800">
          Expires at (optional)
          <input
            type="datetime-local"
            className="mt-1 w-full rounded border border-gray-300 px-2 py-1 text-sm"
            value={expiresAt}
            onChange={(e) => setExpiresAt(e.target.value)}
          />
        </label>

        {formError ? <p className="text-sm text-red-700">{formError}</p> : null}

        <button
          type="submit"
          disabled={createShare.isPending}
          className="rounded bg-blue-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-50 hover:bg-blue-800"
        >
          {createShare.isPending ? "Creating…" : "Create share"}
        </button>
      </form>

      {createSuccess ? (
        <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-950">
          <p className="font-medium">Share created. Copy these values — OTP is not stored.</p>
          {Object.entries(createSuccess).map(([k, v]) => (
            <p key={k} className="mt-1 break-all">
              <span className="font-medium">{k}:</span> {String(v)}
            </p>
          ))}
          <button
            type="button"
            onClick={() => setCreateSuccess(null)}
            className="mt-2 text-xs text-emerald-700 underline"
          >
            Dismiss
          </button>
        </div>
      ) : null}

      {/* Shares list */}
      <div>
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-medium text-gray-900">My shares</h2>
          <button
            type="button"
            onClick={() => void refetch()}
            className="text-sm text-blue-700 underline"
          >
            Refresh
          </button>
        </div>

        {isLoading ? (
          <p className="text-sm text-gray-600">Loading…</p>
        ) : isError ? (
          <p className="text-sm text-red-700">Failed to load shares.</p>
        ) : shares.length === 0 ? (
          <p className="text-sm text-gray-600 rounded-lg border border-gray-200 bg-white p-4">
            No shares yet.
          </p>
        ) : (
          <ul className="space-y-3">
            {shares.map((share) => (
              <ShareCard
                key={share.id}
                share={share}
                healthId={healthId}
                onRevoke={handleRevoke}
                revoking={revokingId === share.id}
              />
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
