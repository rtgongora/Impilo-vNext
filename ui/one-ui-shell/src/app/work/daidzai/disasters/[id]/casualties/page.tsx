"use client";

/**
 * MCI casualty tracking — sieve tags a casualty into a mass-casualty incident, and triggers PCT's
 * bulk-mint (one emergency_episode per not-yet-minted casualty). Deferred from W11 (backend only)
 * to W15's experience layer, per that wave's own notes: "Deliberately scoped OUT building a full
 * daidzai-facing BFF client for casualty tagging/listing UI in W11."
 * Route: /work/daidzai/disasters/[id]/casualties
 */

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { AlertTriangle, Loader2, RefreshCw, Tag, Zap } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";
import { useFacilityStore } from "@/hooks/useFacilityStore";

interface Casualty {
  id: string;
  casualtyReference: string;
  sieveCategory: string;
  status: string;
  subjectIdentityMode: string;
  subjectCpid?: string | null;
  emergencyEpisodeId?: string | null;
  taggedBy: string;
  taggedAt: string;
}

const SIEVE_CATEGORIES = ["UNASSESSED", "IMMEDIATE", "URGENT", "DELAYED", "EXPECTANT", "DECEASED"] as const;
const SIEVE_TONE: Record<string, string> = {
  IMMEDIATE: "bg-red-100 text-red-800",
  URGENT: "bg-orange-100 text-orange-800",
  DELAYED: "bg-amber-100 text-amber-800",
  EXPECTANT: "bg-purple-100 text-purple-800",
  DECEASED: "bg-neutral-200 text-neutral-700",
  UNASSESSED: "bg-neutral-100 text-neutral-600",
};

function asArray(p: unknown): Casualty[] {
  return Array.isArray(p) ? (p as Casualty[]) : [];
}
function errMessage(e: unknown): string {
  if (e && typeof e === "object") {
    const obj = e as { error?: { message?: string }; status?: number };
    if (obj.error?.message) return obj.error.message;
    if (obj.status) return `Request failed (HTTP ${obj.status}).`;
  }
  return "Request failed. Please try again.";
}

export default function MciCasualtiesPage({ params }: { params: { id: string } }) {
  const incidentId = params.id;
  const facility = useFacilityStore((s) => s.facility);
  const [data, setData] = useState<Casualty[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [mintResult, setMintResult] = useState<string | null>(null);
  const [form, setForm] = useState({ casualtyReference: "", sieveCategory: "UNASSESSED", taggedBy: "" });

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(asArray(await apiClient.get<unknown>(
        `/internal/v1/daidzai/disasters/${encodeURIComponent(incidentId)}/casualties`)));
    } catch (e) {
      setError(errMessage(e));
      setData(null);
    } finally {
      setLoading(false);
    }
  }, [incidentId]);

  useEffect(() => {
    void load();
  }, [load]);

  const tag = async () => {
    if (!form.casualtyReference.trim() || !form.taggedBy.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await apiClient.post(`/internal/v1/daidzai/disasters/${encodeURIComponent(incidentId)}/casualties`, form);
      setForm({ casualtyReference: "", sieveCategory: "UNASSESSED", taggedBy: "" });
      await load();
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const rerate = async (casualtyId: string, status: string) => {
    setBusy(true);
    try {
      await apiClient.post(
        `/internal/v1/daidzai/disasters/${encodeURIComponent(incidentId)}/casualties/${encodeURIComponent(casualtyId)}/status`,
        { status },
      );
      await load();
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const bulkMint = async () => {
    if (!facility?.id) {
      setError("Select a facility before minting emergency episodes.");
      return;
    }
    setBusy(true);
    setError(null);
    setMintResult(null);
    try {
      const result = await apiClient.post<{ data: Array<{ created: boolean }> }>(
        `/internal/v1/emergency-episodes/mci/${encodeURIComponent(incidentId)}/bulk-mint?facilityId=${encodeURIComponent(facility.id)}`,
        {},
      );
      const minted = result?.data ?? [];
      const created = minted.filter((r) => r.created).length;
      setMintResult(`${created} new emergency episode(s) minted (${minted.length - created} already existed).`);
      await load();
    } catch (e) {
      setError(errMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const unminted = (data ?? []).filter((c) => !c.emergencyEpisodeId);

  return (
    <AppLayout>
      <PageShell
        title="MCI casualty tracking"
        subtitle={`Sieve triage tags for incident ${incidentId} — bulk-mint opens one emergency episode per casualty`}
        serviceSlug="daidzai"
      >
        <Link href="/work/daidzai/disasters" className="mb-4 inline-block text-sm text-primary underline">
          ← Disaster command
        </Link>

        <div className="mb-5 grid gap-3 rounded-xl border border-border bg-card p-4 shadow-sm sm:grid-cols-4">
          <label className="text-sm sm:col-span-2">
            <span className="mb-1 block font-medium">Casualty reference</span>
            <input
              value={form.casualtyReference}
              onChange={(e) => setForm({ ...form, casualtyReference: e.target.value })}
              placeholder="e.g. C-014"
              className="w-full rounded-lg border border-border bg-background px-3 py-2"
            />
          </label>
          <label className="text-sm">
            <span className="mb-1 block font-medium">Sieve category</span>
            <select
              value={form.sieveCategory}
              onChange={(e) => setForm({ ...form, sieveCategory: e.target.value })}
              className="w-full rounded-lg border border-border bg-background px-3 py-2"
            >
              {SIEVE_CATEGORIES.map((c) => (
                <option key={c} value={c}>{c}</option>
              ))}
            </select>
          </label>
          <label className="text-sm">
            <span className="mb-1 block font-medium">Tagged by</span>
            <input
              value={form.taggedBy}
              onChange={(e) => setForm({ ...form, taggedBy: e.target.value })}
              className="w-full rounded-lg border border-border bg-background px-3 py-2"
            />
          </label>
          <div className="sm:col-span-4">
            <button
              type="button"
              onClick={() => void tag()}
              disabled={busy || !form.casualtyReference.trim() || !form.taggedBy.trim()}
              className="inline-flex items-center gap-1.5 rounded-lg bg-red-600 px-4 py-2 text-sm font-semibold text-white hover:bg-red-700 disabled:opacity-60"
            >
              {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Tag className="h-4 w-4" />}
              Tag casualty
            </button>
          </div>
        </div>

        <div className="mb-5 flex flex-wrap items-center gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4">
          <div className="text-sm text-amber-900">
            <strong>{unminted.length}</strong> casualt{unminted.length === 1 ? "y" : "ies"} not yet minted onto an
            emergency episode.
          </div>
          <button
            type="button"
            onClick={() => void bulkMint()}
            disabled={busy || unminted.length === 0 || !facility?.id}
            className="ml-auto inline-flex items-center gap-1.5 rounded-lg bg-amber-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-amber-700 disabled:opacity-60"
          >
            <Zap className="h-3.5 w-3.5" /> Bulk-mint episodes
          </button>
          <button
            type="button"
            onClick={() => void load()}
            className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-sm text-muted-foreground hover:text-foreground"
          >
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </button>
        </div>
        {!facility?.id && (
          <p className="mb-3 text-xs text-muted-foreground">Select a receiving facility to enable bulk-mint.</p>
        )}
        {mintResult && (
          <p className="mb-3 rounded-lg border border-emerald-200 bg-emerald-50 p-2 text-sm text-emerald-800">
            {mintResult}
          </p>
        )}

        {loading ? (
          <div className="flex items-center gap-2 py-16 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin text-teal-600" /> Loading casualties…
          </div>
        ) : error ? (
          <div className="rounded-xl border border-red-200 bg-red-50 p-5 text-sm text-red-800">
            <div className="mb-2 flex items-center gap-2 font-semibold">
              <AlertTriangle className="h-4 w-4" /> {error}
            </div>
          </div>
        ) : !data || data.length === 0 ? (
          <div className="rounded-xl border border-border bg-card p-8 text-center text-sm text-muted-foreground">
            No casualties tagged yet.
          </div>
        ) : (
          <ul className="space-y-2">
            {data.map((c) => (
              <li
                key={c.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-card p-3 shadow-sm"
              >
                <div>
                  <div className="text-sm font-semibold">
                    {c.casualtyReference}
                    <span className={`ml-2 rounded px-2 py-0.5 text-xs font-medium ${SIEVE_TONE[c.sieveCategory] ?? ""}`}>
                      {c.sieveCategory}
                    </span>
                  </div>
                  <div className="text-xs text-muted-foreground">
                    {c.status} · {c.subjectCpid ?? "identity unresolved"} · tagged by {c.taggedBy}
                    {c.emergencyEpisodeId ? (
                      <Link href={`/clinical/emergency/spine/${c.emergencyEpisodeId}`} className="ml-2 text-primary underline">
                        episode
                      </Link>
                    ) : (
                      <span className="ml-2 text-amber-700">not minted</span>
                    )}
                  </div>
                </div>
                {c.status !== "AT_FACILITY" && c.status !== "DECEASED_CONFIRMED" && (
                  <select
                    value={c.status}
                    onChange={(e) => void rerate(c.id, e.target.value)}
                    disabled={busy}
                    className="rounded-lg border border-border bg-background px-2 py-1 text-xs"
                    aria-label={`Status for ${c.casualtyReference}`}
                  >
                    {["ON_SCENE", "TRANSPORTED", "AT_FACILITY", "DECEASED_CONFIRMED"].map((s) => (
                      <option key={s} value={s}>{s.replaceAll("_", " ")}</option>
                    ))}
                  </select>
                )}
              </li>
            ))}
          </ul>
        )}
      </PageShell>
    </AppLayout>
  );
}
