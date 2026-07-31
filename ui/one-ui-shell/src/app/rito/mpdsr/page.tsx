"use client";

/**
 * MPDSR review worklist — QI/ops only. Proxies experience-BFF → Rito.
 * Route: /rito/mpdsr
 */

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { HeartPulse, Loader2, RefreshCw } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";

interface MpdsrReview {
  id: string;
  deathCaseId?: string;
  status?: string;
  reviewLevel?: string;
  facilityId?: string;
  triggerFlags?: string;
  openedAt?: string;
}

function asArray(payload: unknown): MpdsrReview[] {
  if (Array.isArray(payload)) return payload as MpdsrReview[];
  if (payload && typeof payload === "object") {
    const inner = (payload as { data?: unknown }).data;
    if (Array.isArray(inner)) return inner as MpdsrReview[];
  }
  return [];
}

export default function MpdsrReviewsPage() {
  const [data, setData] = useState<MpdsrReview[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await apiClient.get<unknown>("/internal/v1/rito/mpdsr/reviews");
      setData(asArray(res));
    } catch {
      setError("Could not load MPDSR reviews.");
      setData(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <AppLayout>
      <PageShell
        title="MPDSR Reviews"
        subtitle="Confidential maternal & perinatal death surveillance — committee work, not the EHR maternity chart"
        serviceSlug="rito"
      >
        <div className="mb-4 flex items-center justify-between">
          <p className="text-sm text-muted-foreground">
            Reviews open automatically from PCT <code className="text-xs">DEATH_REVIEW_REQUIRED</code> events.
            Facility readiness prompts are firewalled — no review content crosses.
          </p>
          <button
            type="button"
            onClick={() => void load()}
            className="inline-flex items-center gap-2 rounded-lg border border-border px-3 py-1.5 text-sm hover:bg-muted"
          >
            {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
            Refresh
          </button>
        </div>

        {error && (
          <div className="mb-4 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-800">{error}</div>
        )}

        {loading && !data && (
          <div className="flex items-center gap-2 text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading reviews…
          </div>
        )}

        {data && data.length === 0 && !loading && (
          <div className="rounded-2xl border border-dashed border-border p-8 text-center text-muted-foreground">
            No MPDSR reviews yet. Death cases flagged for review in PCT will appear here.
          </div>
        )}

        {data && data.length > 0 && (
          <div className="overflow-hidden rounded-2xl border border-border">
            <table className="min-w-full text-sm">
              <thead className="bg-muted/50 text-left">
                <tr>
                  <th className="px-4 py-3 font-medium">Review</th>
                  <th className="px-4 py-3 font-medium">Level</th>
                  <th className="px-4 py-3 font-medium">Flags</th>
                  <th className="px-4 py-3 font-medium">Status</th>
                  <th className="px-4 py-3 font-medium">Opened</th>
                </tr>
              </thead>
              <tbody>
                {data.map((r) => (
                  <tr key={r.id} className="border-t border-border hover:bg-muted/30">
                    <td className="px-4 py-3">
                      <Link
                        href={`/rito/mpdsr/${r.id}`}
                        className="inline-flex items-center gap-2 font-medium text-teal-700 hover:underline"
                      >
                        <HeartPulse className="h-4 w-4" />
                        {r.id.slice(0, 8)}…
                      </Link>
                    </td>
                    <td className="px-4 py-3">{r.reviewLevel ?? "—"}</td>
                    <td className="px-4 py-3">{r.triggerFlags ?? "—"}</td>
                    <td className="px-4 py-3">
                      <span className="rounded-full bg-teal-50 px-2 py-0.5 text-xs font-medium text-teal-800">
                        {r.status ?? "OPEN"}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-muted-foreground">
                      {r.openedAt ? new Date(r.openedAt).toLocaleString() : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
