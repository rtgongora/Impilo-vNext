"use client";

/**
 * Ruvimbo Performance (/ruvimbo/performance) — oversight scorecards for the financing
 * platform. Every figure is a REAL, tenant-scoped aggregate from the coverage service's
 * /performance/summary endpoint (live COUNT/GROUP BY over claims, authorisations,
 * memberships, the claims switch, integrity flags and the payer ecosystem). No estimates,
 * no fabricated statistics — empty tenants simply read zero.
 */

import { useState } from "react";
import { BarChart3, Radio, ShieldAlert, TrendingUp, Users } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { usePerformanceSummary, useFraudFlags } from "@/hooks/queries/useRuvimbo";
import { RuvimboSection } from "@/components/ruvimbo/RuvimboSection";
import { RuvimboSummaryCard } from "@/components/ruvimbo/RuvimboSummaryCard";
import { RuvimboSubNav, type RuvimboTab } from "@/components/ruvimbo/RuvimboSubNav";
import { RuvimboEmptyState } from "@/components/ruvimbo/RuvimboEmptyState";
import { asRecord, readStr, readNum, statusTone } from "@/components/ruvimbo/util";

type TabKey = "overview" | "switch" | "integrity" | "analytics";
const rows = (d: unknown) => (Array.isArray(d) ? d : []).map(asRecord);
function Badge({ status }: { status: string }) {
  return <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${statusTone(status)}`}>{status || "—"}</span>;
}
function sum(m: Record<string, unknown>, ...keys: string[]): number {
  return keys.reduce((t, k) => t + readNum(m, k), 0);
}

export default function RuvimboPerformancePage() {
  const [tab, setTab] = useState<TabKey>("overview");
  const summaryQ = usePerformanceSummary();
  const fraudQ = useFraudFlags("OPEN");

  const s = asRecord(summaryQ.data);
  const coverage = asRecord(s.coverage);
  const claims = asRecord(s.claims);
  const claimsBy = asRecord(claims.byStatus);
  const auths = asRecord(s.authorisations);
  const authsBy = asRecord(auths.byStatus);
  const sw = asRecord(s.switch);
  const swBy = asRecord(sw.byStatus);
  const integrity = asRecord(s.integrity);

  const swTotal = readNum(sw, "total");
  const swAccepted = readNum(swBy, "ACCEPTED");
  const swPending = readNum(swBy, "PENDING");
  const acceptanceRate = swTotal ? Math.round((swAccepted / swTotal) * 100) : null;

  const claimsTotal = readNum(claims, "total");
  const claimsApproved = sum(claimsBy, "APPROVED", "PARTIALLY_APPROVED", "PAID");
  const claimsDenied = sum(claimsBy, "DENIED", "REJECTED");
  const approvalRate = claimsTotal ? Math.round((claimsApproved / claimsTotal) * 100) : null;
  const denialRate = claimsTotal ? Math.round((claimsDenied / claimsTotal) * 100) : null;

  const authsTotal = readNum(auths, "total");
  const authsApproved = sum(authsBy, "APPROVED", "PARTIALLY_APPROVED");
  const authApprovalRate = authsTotal ? Math.round((authsApproved / authsTotal) * 100) : null;

  const openFlags = readNum(integrity, "openFlags") || rows(fraudQ.data).length;

  const tabs: RuvimboTab[] = [
    { key: "overview", label: "Executive overview", icon: <BarChart3 className="h-4 w-4" /> },
    { key: "switch", label: "Claims switch", icon: <Radio className="h-4 w-4" /> },
    { key: "integrity", label: "Integrity", icon: <ShieldAlert className="h-4 w-4" />, count: openFlags },
    { key: "analytics", label: "Financing analytics", icon: <TrendingUp className="h-4 w-4" /> },
  ];

  const loading = summaryQ.isLoading;

  return (
    <AppLayout>
      <PageShell title="Ruvimbo Performance" subtitle="Financing, claims, payer, provider and programme monitoring" serviceSlug="ruvimbo">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <RuvimboSummaryCard icon={<Users className="h-4 w-4" />} label="Active coverage" value={readNum(coverage, "activeMembers").toLocaleString()} hint={`${readNum(coverage, "totalMembers").toLocaleString()} total memberships`} tone="positive" isLoading={loading} onClick={() => setTab("analytics")} />
          <RuvimboSummaryCard icon={<Radio className="h-4 w-4" />} label="Switch acceptance" value={acceptanceRate === null ? "—" : `${acceptanceRate}%`} hint={swTotal ? `${swTotal} transactions` : "no transactions"} tone={acceptanceRate !== null && acceptanceRate < 80 ? "warning" : "positive"} isLoading={loading} onClick={() => setTab("switch")} />
          <RuvimboSummaryCard icon={<TrendingUp className="h-4 w-4" />} label="Claim approval rate" value={approvalRate === null ? "—" : `${approvalRate}%`} hint={claimsTotal ? `${claimsTotal} claims` : "no claims"} tone="brand" isLoading={loading} onClick={() => setTab("analytics")} />
          <RuvimboSummaryCard icon={<ShieldAlert className="h-4 w-4" />} label="Integrity flags" value={openFlags} hint="open, awaiting review" tone={openFlags ? "danger" : "neutral"} isLoading={loading || fraudQ.isLoading} onClick={() => setTab("integrity")} />
        </div>

        <RuvimboSubNav tabs={tabs} active={tab} onChange={(k) => setTab(k as TabKey)} />

        <div className="space-y-4">
          {tab === "overview" && (
            <RuvimboSection title="Claims switch throughput" icon={<Radio className="h-4 w-4 text-violet-700" />} isLoading={loading} isError={summaryQ.isError} onRetry={() => summaryQ.refetch()}>
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                {[
                  { label: "Routed", n: readNum(swBy, "ROUTED"), tone: "bg-blue-100 text-blue-800" },
                  { label: "Pending", n: swPending, tone: "bg-amber-100 text-warning-foreground" },
                  { label: "Accepted", n: swAccepted, tone: "bg-green-100 text-green-800" },
                  { label: "Rejected", n: readNum(swBy, "REJECTED"), tone: "bg-red-100 text-red-800" },
                ].map((c) => (
                  <div key={c.label} className="rounded-lg border border-border p-3 text-center">
                    <p className="text-2xl font-bold text-foreground">{c.n}</p>
                    <span className={`mt-1 inline-block rounded-full px-2 py-0.5 text-xs font-medium ${c.tone}`}>{c.label}</span>
                  </div>
                ))}
              </div>
              <p className="mt-3 text-xs text-muted-foreground">Live counts from the claims-switch gateway. Acceptance rate = accepted / all transactions.</p>
            </RuvimboSection>
          )}

          {tab === "switch" && <StatusDistribution title="Claims switch — status distribution" byStatus={swBy} total={swTotal} emptyTitle="No claims-switch transactions yet" emptyExplain="As claims route between providers and payers, throughput, acceptance and backlog appear here." />}

          {tab === "integrity" && (
            <RuvimboSection title="Integrity — open flags" icon={<ShieldAlert className="h-4 w-4 text-violet-700" />} isLoading={fraudQ.isLoading} isError={fraudQ.isError} onRetry={() => fraudQ.refetch()} isEmpty={!rows(fraudQ.data).length}
              emptyState={<RuvimboEmptyState title="No open integrity flags" explanation="Suspected duplicate claims, claims after termination and improbable amounts appear here for oversight." when="Flags are raised automatically as claims are screened." />}
            >
              <ul className="divide-y divide-border">
                {rows(fraudQ.data).map((f, i) => (
                  <li key={readStr(f, "id") || i} className="flex items-center justify-between gap-2 py-2 text-sm">
                    <span className="truncate text-foreground">{readStr(f, "flagType", "type") || "Flag"}</span>
                    <Badge status={readStr(f, "status")} />
                  </li>
                ))}
              </ul>
            </RuvimboSection>
          )}

          {tab === "analytics" && (
            <>
              <RuvimboSection title="Coverage & claims" icon={<TrendingUp className="h-4 w-4 text-violet-700" />} isLoading={loading} isError={summaryQ.isError} onRetry={() => summaryQ.refetch()}>
                <div className="grid gap-3 sm:grid-cols-3">
                  <Metric label="Active coverage" value={readNum(coverage, "activeMembers").toLocaleString()} sub={`${readNum(coverage, "totalMembers").toLocaleString()} total`} />
                  <Metric label="Claim approval rate" value={approvalRate === null ? "—" : `${approvalRate}%`} sub={`${claimsApproved} of ${claimsTotal}`} />
                  <Metric label="Claim denial rate" value={denialRate === null ? "—" : `${denialRate}%`} sub={`${claimsDenied} denied`} tone={denialRate !== null && denialRate > 20 ? "danger" : "neutral"} />
                </div>
              </RuvimboSection>
              <StatusDistribution title="Claims by status" byStatus={claimsBy} total={claimsTotal} emptyTitle="No claims yet" emptyExplain="Claim volume and adjudication outcomes appear here as facilities submit claims." />
              <RuvimboSection title="Authorisations" icon={<TrendingUp className="h-4 w-4 text-violet-700" />} isLoading={loading}>
                <div className="grid gap-3 sm:grid-cols-2">
                  <Metric label="Authorisation approval rate" value={authApprovalRate === null ? "—" : `${authApprovalRate}%`} sub={`${authsApproved} of ${authsTotal}`} />
                  <Metric label="Total authorisations" value={authsTotal.toLocaleString()} sub="all statuses" />
                </div>
              </RuvimboSection>
            </>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}

function Metric({ label, value, sub, tone = "neutral" }: { label: string; value: string; sub?: string; tone?: "neutral" | "danger" }) {
  return (
    <div className="rounded-lg border border-border p-3">
      <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className={`mt-1 text-2xl font-bold ${tone === "danger" ? "text-danger" : "text-foreground"}`}>{value}</p>
      {sub ? <p className="text-xs text-muted-foreground">{sub}</p> : null}
    </div>
  );
}

function StatusDistribution({ title, byStatus, total, emptyTitle, emptyExplain }: { title: string; byStatus: Record<string, unknown>; total: number; emptyTitle: string; emptyExplain: string }) {
  const entries = Object.entries(byStatus).map(([status, v]) => ({ status, n: typeof v === "number" ? v : Number(v) || 0 }));
  const cls = (st: string) => statusTone(st).split(" ")[0].replace("bg-", "bg-").replace("-100", "-500");
  return (
    <RuvimboSection title={title} icon={<Radio className="h-4 w-4 text-violet-700" />}>
      {total === 0 ? (
        <RuvimboEmptyState title={emptyTitle} explanation={emptyExplain} when="Figures populate as activity flows through the platform." />
      ) : (
        <div className="space-y-3 text-sm">
          {entries.map((r) => (
            <div key={r.status}>
              <div className="flex justify-between"><span className="text-foreground">{r.status}</span><span className="text-muted-foreground">{r.n}</span></div>
              <div className="mt-1 h-2 overflow-hidden rounded-full bg-muted">
                <div className={`h-full ${cls(r.status)}`} style={{ width: total ? `${(r.n / total) * 100}%` : "0%" }} />
              </div>
            </div>
          ))}
        </div>
      )}
    </RuvimboSection>
  );
}
