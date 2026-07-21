"use client";

/**
 * Ruvimbo Performance (/ruvimbo/performance) — oversight scorecards for the financing
 * platform. Every figure shown is a REAL count derived from live coverage-service reads
 * (claims-switch transactions by status, integrity flags, ecosystem). Deeper analytics
 * that require server-side aggregation (cost-per-member, denial and turnaround rates) are
 * shown as honest "pending the analytics endpoint" states rather than invented numbers —
 * that aggregation is the tracked R5 backend seam.
 */

import { useState } from "react";
import { BarChart3, Radio, ShieldAlert, Landmark, TrendingUp } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useGatewayTransactions, useFraudFlags, usePayers, useEmployers } from "@/hooks/queries/useRuvimbo";
import { RuvimboSection } from "@/components/ruvimbo/RuvimboSection";
import { RuvimboSummaryCard } from "@/components/ruvimbo/RuvimboSummaryCard";
import { RuvimboSubNav, type RuvimboTab } from "@/components/ruvimbo/RuvimboSubNav";
import { RuvimboEmptyState } from "@/components/ruvimbo/RuvimboEmptyState";
import { asRecord, readStr, statusTone } from "@/components/ruvimbo/util";

type TabKey = "overview" | "switch" | "integrity" | "analytics";
const rows = (d: unknown) => (Array.isArray(d) ? d : []).map(asRecord);
function Badge({ status }: { status: string }) {
  return <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${statusTone(status)}`}>{status || "—"}</span>;
}

export default function RuvimboPerformancePage() {
  const [tab, setTab] = useState<TabKey>("overview");

  const routedQ = useGatewayTransactions("ROUTED");
  const pendingQ = useGatewayTransactions("PENDING");
  const acceptedQ = useGatewayTransactions("ACCEPTED");
  const rejectedQ = useGatewayTransactions("REJECTED");
  const fraudQ = useFraudFlags("OPEN");
  const payersQ = usePayers();
  const employersQ = useEmployers();

  const routed = rows(routedQ.data).length;
  const pending = rows(pendingQ.data).length;
  const accepted = rows(acceptedQ.data).length;
  const rejected = rows(rejectedQ.data).length;
  const total = routed + pending + accepted + rejected;
  const acceptanceRate = total ? Math.round((accepted / total) * 100) : null;
  const fraudOpen = rows(fraudQ.data).length;
  const payers = rows(payersQ.data).length;
  const employers = rows(employersQ.data).length;

  const tabs: RuvimboTab[] = [
    { key: "overview", label: "Executive overview", icon: <BarChart3 className="h-4 w-4" /> },
    { key: "switch", label: "Claims switch", icon: <Radio className="h-4 w-4" /> },
    { key: "integrity", label: "Integrity", icon: <ShieldAlert className="h-4 w-4" />, count: fraudOpen },
    { key: "analytics", label: "Financing analytics", icon: <TrendingUp className="h-4 w-4" /> },
  ];

  return (
    <AppLayout>
      <PageShell title="Ruvimbo Performance" subtitle="Financing, claims, payer, provider and programme monitoring" serviceSlug="ruvimbo">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <RuvimboSummaryCard icon={<Radio className="h-4 w-4" />} label="Switch acceptance" value={acceptanceRate === null ? "—" : `${acceptanceRate}%`} hint={total ? `${total} transactions` : "no transactions yet"} tone={acceptanceRate !== null && acceptanceRate < 80 ? "warning" : "positive"} isLoading={acceptedQ.isLoading} onClick={() => setTab("switch")} />
          <RuvimboSummaryCard icon={<Radio className="h-4 w-4" />} label="Switch backlog" value={pending} hint="pending a response" tone={pending ? "warning" : "neutral"} isLoading={pendingQ.isLoading} onClick={() => setTab("switch")} />
          <RuvimboSummaryCard icon={<ShieldAlert className="h-4 w-4" />} label="Integrity flags" value={fraudOpen} hint="open, awaiting review" tone={fraudOpen ? "danger" : "neutral"} isLoading={fraudQ.isLoading} onClick={() => setTab("integrity")} />
          <RuvimboSummaryCard icon={<Landmark className="h-4 w-4" />} label="Ecosystem" value={payers} hint={`payers · ${employers} employers`} tone="brand" isLoading={payersQ.isLoading} />
        </div>

        <RuvimboSubNav tabs={tabs} active={tab} onChange={(k) => setTab(k as TabKey)} />

        <div className="space-y-4">
          {tab === "overview" && (
            <RuvimboSection title="Claims switch throughput" icon={<Radio className="h-4 w-4 text-violet-700" />} isLoading={routedQ.isLoading}>
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                {[
                  { label: "Routed", n: routed, tone: "bg-blue-100 text-blue-800" },
                  { label: "Pending", n: pending, tone: "bg-amber-100 text-warning-foreground" },
                  { label: "Accepted", n: accepted, tone: "bg-green-100 text-green-800" },
                  { label: "Rejected", n: rejected, tone: "bg-red-100 text-red-800" },
                ].map((c) => (
                  <div key={c.label} className="rounded-lg border border-border p-3 text-center">
                    <p className="text-2xl font-bold text-foreground">{c.n}</p>
                    <span className={`mt-1 inline-block rounded-full px-2 py-0.5 text-xs font-medium ${c.tone}`}>{c.label}</span>
                  </div>
                ))}
              </div>
              <p className="mt-3 text-xs text-muted-foreground">Counts are live from the claims-switch gateway. Acceptance rate = accepted / all routed transactions.</p>
            </RuvimboSection>
          )}

          {tab === "switch" && <SwitchDetail routed={routed} pending={pending} accepted={accepted} rejected={rejected} />}

          {tab === "integrity" && (
            <RuvimboSection title="Integrity — open flags" icon={<ShieldAlert className="h-4 w-4 text-violet-700" />} isLoading={fraudQ.isLoading} isError={fraudQ.isError} onRetry={() => fraudQ.refetch()} isEmpty={!fraudOpen}
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
            <RuvimboSection title="Financing analytics" icon={<TrendingUp className="h-4 w-4 text-violet-700" />}>
              <RuvimboEmptyState
                icon={<BarChart3 className="h-6 w-6" />}
                title="Cost, denial-rate and turnaround analytics are being added"
                explanation="Cost per member, clean-claim and denial rates, adjudication and payment turnaround, and drill-down by payer / scheme / provider / facility / province / programme require server-side aggregation across claims and memberships."
                when="These land once the Ruvimbo performance aggregation endpoints are added to the coverage service — the tracked R5 backend seam. Until then, only live counts are shown, never estimated figures."
              />
            </RuvimboSection>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}

function SwitchDetail({ routed, pending, accepted, rejected }: { routed: number; pending: number; accepted: number; rejected: number }) {
  const total = routed + pending + accepted + rejected;
  const bar = (n: number, cls: string) => (
    <div className="h-2 overflow-hidden rounded-full bg-muted">
      <div className={`h-full ${cls}`} style={{ width: total ? `${(n / total) * 100}%` : "0%" }} />
    </div>
  );
  return (
    <RuvimboSection title="Claims switch — status distribution" icon={<Radio className="h-4 w-4 text-violet-700" />}>
      {total === 0 ? (
        <RuvimboEmptyState title="No claims-switch transactions yet" explanation="As claims are routed between providers and payers, throughput, acceptance and backlog appear here." when="Transactions flow once claims are submitted through the switch." />
      ) : (
        <div className="space-y-3 text-sm">
          {[
            { label: "Routed", n: routed, cls: "bg-blue-500" },
            { label: "Pending", n: pending, cls: "bg-amber-500" },
            { label: "Accepted", n: accepted, cls: "bg-green-500" },
            { label: "Rejected", n: rejected, cls: "bg-red-500" },
          ].map((r) => (
            <div key={r.label}>
              <div className="flex justify-between"><span className="text-foreground">{r.label}</span><span className="text-muted-foreground">{r.n}</span></div>
              <div className="mt-1">{bar(r.n, r.cls)}</div>
            </div>
          ))}
        </div>
      )}
    </RuvimboSection>
  );
}
