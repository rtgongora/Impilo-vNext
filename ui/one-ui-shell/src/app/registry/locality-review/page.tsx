"use client";

/**
 * Review pending Tuso locality proposals (approve / reject) via Experience BFF.
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient, type ApiResponse } from "@/lib/api-client";

type Proposal = {
  id: number;
  districtCode: string;
  wardCode?: string | null;
  proposedName: string;
  status: string;
};

export default function LocalityReviewPage() {
  const qc = useQueryClient();
  const { data, isLoading, error } = useQuery({
    queryKey: ["locality-proposals-pending"],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Proposal[]>>("/internal/v1/registry/localities/proposals/pending");
      return Array.isArray(res.data) ? (res.data as Proposal[]) : [];
    },
  });

  const approve = useMutation({
    mutationFn: (id: number) =>
      apiClient.post<ApiResponse<unknown>>(`/internal/v1/registry/localities/proposals/${id}/approve`, {
        reviewerNote: "approved",
      }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["locality-proposals-pending"] }),
  });

  const reject = useMutation({
    mutationFn: (id: number) =>
      apiClient.post<ApiResponse<unknown>>(`/internal/v1/registry/localities/proposals/${id}/reject`, {
        reviewerNote: "rejected",
      }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ["locality-proposals-pending"] }),
  });

  const rows = data ?? [];

  return (
    <AppLayout>
      <PageShell title="Locality gazetteer review" subtitle="Pending proposals from Tuso">
        {isLoading ? <p className="text-sm text-muted-foreground">Loading…</p> : null}
        {error ? <p className="text-sm text-red-600">Failed to load proposals.</p> : null}
        {!isLoading && rows.length === 0 ? (
          <p className="text-sm text-muted-foreground">No pending proposals.</p>
        ) : (
          <ul className="space-y-2 max-w-2xl">
            {rows.map((p) => (
              <li key={p.id} className="rounded-lg border border-border bg-card p-3 flex flex-wrap justify-between gap-2">
                <div>
                  <p className="text-sm font-medium text-foreground">{p.proposedName}</p>
                  <p className="text-xs text-muted-foreground">
                    District {p.districtCode}
                    {p.wardCode ? ` · Ward ${p.wardCode}` : ""}
                  </p>
                </div>
                <div className="flex gap-2">
                  <button
                    type="button"
                    className="text-xs px-2 py-1 rounded bg-emerald-600 text-white disabled:opacity-50"
                    disabled={approve.isPending}
                    onClick={() => approve.mutate(p.id)}
                  >
                    Approve
                  </button>
                  <button
                    type="button"
                    className="text-xs px-2 py-1 rounded bg-neutral-100 text-foreground disabled:opacity-50"
                    disabled={reject.isPending}
                    onClick={() => reject.mutate(p.id)}
                  >
                    Reject
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </PageShell>
    </AppLayout>
  );
}
