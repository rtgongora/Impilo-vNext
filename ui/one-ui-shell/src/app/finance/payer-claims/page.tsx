"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { ArrowLeft, FileSearch, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FinancePayerOpsReconciliationNotice } from "@/components/FinanceAccessNotice";
import { usePayerClaims } from "@/hooks/queries/usePayerClaims";

type ClaimRow = {
  claimId: string;
  billId?: string;
  insurerId?: string;
  status?: string;
  submittedAt?: string | null;
  createdAt?: string | null;
};

function asRecord(value: unknown): Record<string, unknown> | null {
  return value != null && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : null;
}

function extractClaimRows(payload: unknown): ClaimRow[] {
  const root = asRecord(payload);
  const data = asRecord(root?.data) ?? root;
  const items = Array.isArray(data?.items) ? data.items : [];

  return items.reduce<ClaimRow[]>((claims, item) => {
    const row = asRecord(item);
    if (!row) return claims;
    const claimId = typeof row.claimId === "string" ? row.claimId : "";
    if (!claimId) return claims;
    claims.push({
      claimId,
      billId: typeof row.billId === "string" ? row.billId : undefined,
      insurerId: typeof row.insurerId === "string" ? row.insurerId : undefined,
      status: typeof row.status === "string" ? row.status : undefined,
      submittedAt: typeof row.submittedAt === "string" || row.submittedAt === null ? row.submittedAt : undefined,
      createdAt: typeof row.createdAt === "string" || row.createdAt === null ? row.createdAt : undefined,
    });
    return claims;
  }, []);
}

export default function FinancePayerClaimsPage() {
  const [status, setStatus] = useState("");
  const [insurerId, setInsurerId] = useState("");
  const [page, setPage] = useState("0");
  const [size, setSize] = useState("20");
  const [armed, setArmed] = useState(true);

  const claimsQ = usePayerClaims(
    {
      ...(status ? { status } : {}),
      ...(insurerId ? { insurerId } : {}),
      page: Number(page) || 0,
      size: Number(size) || 20,
    },
    armed,
  );

  const claims = useMemo(() => extractClaimRows(claimsQ.data), [claimsQ.data]);

  return (
    <AppLayout>
      <PageShell
        title="Payer claims queue"
        subtitle="MusheX payer-claim listing and handoff into claim detail now run through Experience."
        icon={<FileSearch className="h-6 w-6" />}
      >
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <Link href="/finance/payer-ops" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> Payer ops
          </Link>
          <Link href="/finance/commerce-integrations" className="text-sm text-primary hover:text-primary-hover">
            Commerce & payer stack
          </Link>
        </div>

        <div className="max-w-5xl space-y-6">
          <FinancePayerOpsReconciliationNotice />

          <section className="rounded-xl border border-border bg-card p-5 shadow-sm">
            <h2 className="text-sm font-semibold text-foreground">Claims list</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              GET <code className="text-[11px]">/internal/v1/finance/payer-claims?status=&amp;insurerId=&amp;page=&amp;size=</code>
            </p>

            <div className="mt-4 grid gap-3 md:grid-cols-4">
              <label className="text-xs text-muted-foreground">
                Status
                <input
                  value={status}
                  onChange={(event) => setStatus(event.target.value)}
                  placeholder="SUBMITTED"
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
                />
              </label>
              <label className="text-xs text-muted-foreground">
                Insurer ID
                <input
                  value={insurerId}
                  onChange={(event) => setInsurerId(event.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm font-mono"
                />
              </label>
              <label className="text-xs text-muted-foreground">
                Page
                <input
                  value={page}
                  onChange={(event) => setPage(event.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
                />
              </label>
              <label className="text-xs text-muted-foreground">
                Size
                <input
                  value={size}
                  onChange={(event) => setSize(event.target.value)}
                  className="mt-1 block w-full rounded-lg border border-border px-3 py-2 text-sm"
                />
              </label>
            </div>

            <button
              type="button"
              className="mt-4 rounded-lg border border-border px-4 py-2 text-sm hover:bg-background"
              onClick={() => {
                setArmed(true);
                void claimsQ.refetch();
              }}
            >
              Fetch claims
            </button>

            {claimsQ.isLoading ? (
              <p className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading claims...
              </p>
            ) : claimsQ.isError ? (
              <p className="mt-3 text-sm text-danger">Could not load payer claims.</p>
            ) : claims.length > 0 ? (
              <div className="mt-4 overflow-x-auto rounded-xl border border-border">
                <table className="w-full text-sm">
                  <thead className="bg-background text-left">
                    <tr>
                      <th className="px-3 py-2 font-medium text-foreground">Claim</th>
                      <th className="px-3 py-2 font-medium text-foreground">Bill</th>
                      <th className="px-3 py-2 font-medium text-foreground">Insurer</th>
                      <th className="px-3 py-2 font-medium text-foreground">Status</th>
                      <th className="px-3 py-2 font-medium text-foreground">Submitted</th>
                      <th className="px-3 py-2 font-medium text-foreground">Open</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {claims.map((claim) => (
                      <tr key={claim.claimId}>
                        <td className="px-3 py-3 font-mono text-xs text-foreground">{claim.claimId}</td>
                        <td className="px-3 py-3 text-foreground">{claim.billId ?? "—"}</td>
                        <td className="px-3 py-3 font-mono text-xs text-foreground">{claim.insurerId ?? "—"}</td>
                        <td className="px-3 py-3 text-foreground">{claim.status ?? "—"}</td>
                        <td className="px-3 py-3 text-foreground">{claim.submittedAt ?? "Not submitted"}</td>
                        <td className="px-3 py-3">
                          <Link
                            href={`/finance/payer-claims/${encodeURIComponent(claim.claimId)}`}
                            className="text-primary hover:underline"
                          >
                            Open claim
                          </Link>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="mt-3 text-sm text-muted-foreground">No payer claims matched the current filters.</p>
            )}
          </section>
        </div>
      </PageShell>
    </AppLayout>
  );
}
