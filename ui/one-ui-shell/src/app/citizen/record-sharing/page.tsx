"use client";

import { useState } from "react";
import { apiClient, type ApiResponse } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";

interface ShareRow {
  shareRequestId: number;
  scopeType: string;
  shareStatus: string;
  requestExpiresAt: string;
  grantStatus: string | null;
  trustLevel: string | null;
  hasActiveArtifact: boolean;
}

/** Citizen: create and list governed patient shares (BFF → VITO). */
export default function CitizenRecordSharingPage() {
  const user = useAuthStore((s) => s.user);
  const healthId = user?.healthId;
  const [scopeType, setScopeType] = useState("LIMITED_CLINICAL_SUMMARY");
  const [expiryHours, setExpiryHours] = useState(72);
  const [result, setResult] = useState<Record<string, unknown> | null>(null);
  const [shares, setShares] = useState<ShareRow[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function refreshList() {
    if (!healthId) return;
    setLoading(true);
    setError("");
    try {
      const res = await apiClient.get<ApiResponse<ShareRow[]>>(
        `/internal/v1/citizen/clients/${healthId}/patient-shares`,
      );
      setShares(res.data ?? []);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load shares");
    } finally {
      setLoading(false);
    }
  }

  async function createShare(e: React.FormEvent) {
    e.preventDefault();
    if (!healthId) return;
    setLoading(true);
    setError("");
    setResult(null);
    try {
      const res = await apiClient.post<ApiResponse<Record<string, unknown>>>(
        `/internal/v1/citizen/clients/${healthId}/patient-shares`,
        { scopeType, expiryHours, oneTimeUse: true, revocableFlag: true },
      );
      setResult(res.data ?? null);
      await refreshList();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Create failed");
    } finally {
      setLoading(false);
    }
  }

  if (!healthId) {
    return (
      <div className="max-w-xl p-6 text-sm text-gray-700">
        Sign in with a profile that has a Health ID to manage record sharing.
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl space-y-8 p-6">
      <div>
        <h1 className="text-xl font-semibold text-gray-900">Share my record</h1>
        <p className="mt-1 text-sm text-gray-600">
          Generate a governed link and one-time code for a provider who is not yet fully onboarded on vNext. Access is
          time-bound, revocable, and policy-controlled (Tshepo + Varapi provisional identity).
        </p>
      </div>

      <form onSubmit={createShare} className="space-y-4 rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
        <label className="block text-sm font-medium text-gray-800">
          Scope
          <select
            className="mt-1 w-full rounded border border-gray-300 px-2 py-1 text-sm"
            value={scopeType}
            onChange={(e) => setScopeType(e.target.value)}
          >
            <option value="LIMITED_CLINICAL_SUMMARY">Limited clinical summary</option>
            <option value="ENCOUNTER_CONTEXT">Encounter context</option>
            <option value="REFERRAL_CONTEXT">Referral context</option>
            <option value="DOCUMENT_SET">Document set</option>
          </select>
        </label>
        <label className="block text-sm font-medium text-gray-800">
          Valid for (hours)
          <input
            type="number"
            min={1}
            max={720}
            className="mt-1 w-full rounded border border-gray-300 px-2 py-1 text-sm"
            value={expiryHours}
            onChange={(e) => setExpiryHours(Number(e.target.value))}
          />
        </label>
        <button
          type="submit"
          disabled={loading}
          className="rounded bg-blue-700 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {loading ? "Working…" : "Create share"}
        </button>
      </form>

      {error ? <p className="text-sm text-red-700">{error}</p> : null}

      {result ? (
        <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-950">
          <p className="font-medium">Share created — copy these values once (OTP is not stored).</p>
          <p className="mt-2 break-all">
            <span className="font-medium">Share token:</span> {String(result.shareToken ?? "")}
          </p>
          <p className="mt-1">
            <span className="font-medium">OTP:</span> {String(result.otp ?? "")}
          </p>
          <p className="mt-2 text-xs text-gray-700">
            Provider entry URL (shell):{" "}
            <code className="break-all">
              /collaboration/access?token={encodeURIComponent(String(result.shareToken ?? ""))}
            </code>
          </p>
        </div>
      ) : null}

      <div>
        <div className="mb-2 flex items-center justify-between">
          <h2 className="text-lg font-medium text-gray-900">Active shares</h2>
          <button type="button" onClick={refreshList} className="text-sm text-blue-700 underline">
            Refresh
          </button>
        </div>
        <ul className="divide-y divide-gray-200 rounded-lg border border-gray-200 bg-white text-sm">
          {shares.length === 0 ? (
            <li className="p-3 text-gray-600">No shares yet.</li>
          ) : (
            shares.map((s) => (
              <li key={s.shareRequestId} className="flex flex-col gap-1 p-3">
                <span className="font-medium">
                  #{s.shareRequestId} — {s.scopeType}
                </span>
                <span className="text-gray-600">
                  Status: {s.shareStatus} / grant: {s.grantStatus ?? "—"} / trust: {s.trustLevel ?? "—"}
                </span>
                <span className="text-xs text-gray-500">Expires: {s.requestExpiresAt}</span>
              </li>
            ))
          )}
        </ul>
      </div>
    </div>
  );
}
