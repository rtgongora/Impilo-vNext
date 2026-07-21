"use client";

/**
 * Ruvimbo Administration (/ruvimbo/administration) — national/platform operations:
 * payer onboarding, the claims-switch monitor, employer bulk operations, and platform
 * governance. Wired to real data (usePayers, useGatewayTransactions, useEmployers).
 *
 * Switch remediation re-routes and replays transactions but never edits legitimate claim
 * content. Rules / terminology / integration-health surfaces depend on backend seams not
 * yet built and are shown as honest "being added" states rather than invented dashboards.
 */

import { useState } from "react";
import {
  Settings2,
  Landmark,
  Radio,
  Building2,
  ShieldAlert,
  RefreshCw,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  usePayers,
  useSuspendPayer,
  useReactivatePayer,
  useGatewayTransactions,
  useRouteGateway,
  useEmployers,
} from "@/hooks/queries/useRuvimbo";
import { RuvimboSection } from "@/components/ruvimbo/RuvimboSection";
import { RuvimboSummaryCard } from "@/components/ruvimbo/RuvimboSummaryCard";
import { RuvimboSubNav, type RuvimboTab } from "@/components/ruvimbo/RuvimboSubNav";
import { RuvimboEmptyState } from "@/components/ruvimbo/RuvimboEmptyState";
import { asRecord, readStr, readNum, statusTone } from "@/components/ruvimbo/util";

type TabKey = "overview" | "payers" | "switch" | "bulk" | "platform";

function Badge({ status }: { status: string }) {
  return <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${statusTone(status)}`}>{status || "—"}</span>;
}
const rows = (d: unknown) => (Array.isArray(d) ? d : []).map(asRecord);

export default function RuvimboAdministrationPage() {
  const [tab, setTab] = useState<TabKey>("overview");
  const payersQ = usePayers();
  const gwPendingQ = useGatewayTransactions("PENDING");
  const employersQ = useEmployers();

  const payers = rows(payersQ.data);
  const gwPending = rows(gwPendingQ.data);
  const employers = rows(employersQ.data);

  const tabs: RuvimboTab[] = [
    { key: "overview", label: "Overview", icon: <Settings2 className="h-4 w-4" /> },
    { key: "payers", label: "Payers", icon: <Landmark className="h-4 w-4" /> },
    { key: "switch", label: "Claims switch", icon: <Radio className="h-4 w-4" />, count: gwPending.length },
    { key: "bulk", label: "Bulk operations", icon: <Building2 className="h-4 w-4" /> },
    { key: "platform", label: "Platform", icon: <ShieldAlert className="h-4 w-4" /> },
  ];

  return (
    <AppLayout>
      <PageShell title="Ruvimbo Administration" subtitle="Payers, claims switch, standards and platform governance" serviceSlug="ruvimbo">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <RuvimboSummaryCard icon={<Landmark className="h-4 w-4" />} label="Payers" value={payers.length} hint="registered" tone="brand" isLoading={payersQ.isLoading} onClick={() => setTab("payers")} />
          <RuvimboSummaryCard icon={<Radio className="h-4 w-4" />} label="Switch — pending" value={gwPending.length} hint="transactions awaiting a response" tone={gwPending.length ? "warning" : "neutral"} isLoading={gwPendingQ.isLoading} onClick={() => setTab("switch")} />
          <RuvimboSummaryCard icon={<Building2 className="h-4 w-4" />} label="Employers" value={employers.length} hint="groups" isLoading={employersQ.isLoading} onClick={() => setTab("bulk")} />
          <RuvimboSummaryCard icon={<ShieldAlert className="h-4 w-4" />} label="Platform health" value={<span className="text-base font-medium">Monitored</span>} hint="integration & data quality" onClick={() => setTab("platform")} />
        </div>

        <RuvimboSubNav tabs={tabs} active={tab} onChange={(k) => setTab(k as TabKey)} />

        <div className="space-y-4">
          {tab === "overview" && <SwitchPanel status="PENDING" heading="Claims switch — transactions awaiting response" />}
          {tab === "payers" && <PayersPanel payersQ={payersQ} payers={payers} />}
          {tab === "switch" && <SwitchPanel />}
          {tab === "bulk" && <BulkPanel employersQ={employersQ} employers={employers} />}
          {tab === "platform" && <PlatformPanel />}
        </div>
      </PageShell>
    </AppLayout>
  );
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function PayersPanel({ payersQ, payers }: { payersQ: any; payers: Record<string, unknown>[] }) {
  const suspend = useSuspendPayer();
  const reactivate = useReactivatePayer();
  return (
    <RuvimboSection title="Payer onboarding & lifecycle" icon={<Landmark className="h-4 w-4 text-violet-700" />} isLoading={payersQ.isLoading} isError={payersQ.isError} onRetry={() => payersQ.refetch()} isEmpty={!payers.length}
      emptyState={<RuvimboEmptyState title="No payers registered" explanation="Register medical aids, insurers, employer schemes and Government programmes so they can operate schemes, networks and claims on the platform." when="Payer registration CRUD is available in the operations console." actions={[{ label: "Operations console", href: "/coverage/operations" }]} />}
    >
      <table className="w-full text-sm">
        <thead><tr className="border-b text-left text-muted-foreground"><th className="pb-2 pr-2">Payer</th><th className="pb-2 pr-2">Type</th><th className="pb-2 pr-2">Status</th><th className="pb-2">Action</th></tr></thead>
        <tbody className="divide-y divide-border">
          {payers.map((p, i) => {
            const id = readStr(p, "id");
            const st = readStr(p, "status");
            const suspended = /SUSPEND/i.test(st);
            return (
              <tr key={id || i}>
                <td className="py-2 pr-2 text-foreground">{readStr(p, "name", "payerName") || id.slice(0, 8)}</td>
                <td className="py-2 pr-2 text-muted-foreground">{readStr(p, "payerType", "type") || "—"}</td>
                <td className="py-2 pr-2"><Badge status={st} /></td>
                <td className="py-2">{id ? (suspended ? <button type="button" disabled={reactivate.isPending} onClick={() => reactivate.mutate(id)} className="rounded-md bg-green-600 px-2 py-1 text-xs font-medium text-white hover:bg-green-700 disabled:opacity-50">Reactivate</button> : <button type="button" disabled={suspend.isPending} onClick={() => suspend.mutate(id)} className="rounded-md border border-red-200 px-2 py-1 text-xs font-medium text-danger hover:bg-red-50 disabled:opacity-50">Suspend</button>) : null}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </RuvimboSection>
  );
}

function SwitchPanel({ status: fixedStatus, heading }: { status?: string; heading?: string }) {
  const [status, setStatus] = useState(fixedStatus ?? "ROUTED");
  const q = useGatewayTransactions(status);
  const route = useRouteGateway();
  const list = rows(q.data);
  return (
    <RuvimboSection
      title={heading ?? "Claims switch monitor"}
      icon={<Radio className="h-4 w-4 text-violet-700" />}
      actions={!fixedStatus ? <select value={status} onChange={(e) => setStatus(e.target.value)} className="rounded-md border border-border bg-background px-2 py-1 text-xs">{["ROUTED", "PENDING", "ACCEPTED", "REJECTED"].map((s) => <option key={s} value={s}>{s}</option>)}</select> : undefined}
      isLoading={q.isLoading} isError={q.isError} onRetry={() => q.refetch()} isEmpty={!list.length}
      emptyState={<RuvimboEmptyState title={`No ${status.toLowerCase()} transactions`} explanation="The claims switch routes each transaction to its destination and records technical and business acknowledgements. Remediation re-routes or replays — it never edits claim content." when="Transactions appear as claims are routed between providers and payers." />}
    >
      <table className="w-full text-sm">
        <thead><tr className="border-b text-left text-muted-foreground"><th className="pb-2 pr-2">Transaction</th><th className="pb-2 pr-2">Destination</th><th className="pb-2 pr-2">Tech ack</th><th className="pb-2 pr-2">Status</th><th className="pb-2">Action</th></tr></thead>
        <tbody className="divide-y divide-border">
          {list.map((t, i) => {
            const id = readStr(t, "id");
            return (
              <tr key={id || i}>
                <td className="py-2 pr-2 font-mono text-xs">{(readStr(t, "correlationId", "id") || "").slice(0, 10)}</td>
                <td className="py-2 pr-2 text-muted-foreground">{readStr(t, "destination", "receiver") || "—"}</td>
                <td className="py-2 pr-2 text-muted-foreground">{readStr(t, "technicalAck", "tech_ack") || "—"}</td>
                <td className="py-2 pr-2"><Badge status={readStr(t, "status")} /></td>
                <td className="py-2">{id ? <button type="button" disabled={route.isPending} onClick={() => route.mutate({ transaction_id: id, action: "replay" })} className="inline-flex items-center gap-1 rounded-md border border-border px-2 py-1 text-xs font-medium hover:bg-muted disabled:opacity-50"><RefreshCw className="h-3 w-3" />Replay</button> : null}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </RuvimboSection>
  );
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function BulkPanel({ employersQ, employers }: { employersQ: any; employers: Record<string, unknown>[] }) {
  return (
    <RuvimboSection title="Employer bulk operations" icon={<Building2 className="h-4 w-4 text-violet-700" />} isLoading={employersQ.isLoading} isError={employersQ.isError} onRetry={() => employersQ.refetch()} isEmpty={!employers.length}
      emptyState={<RuvimboEmptyState title="No employers registered" explanation="Employer rosters are staged, validated, then applied to create employer-sponsored memberships in bulk." when="Register an employer and stage a roster to begin." actions={[{ label: "Operations console", href: "/coverage/operations" }]} />}
    >
      <table className="w-full text-sm">
        <thead><tr className="border-b text-left text-muted-foreground"><th className="pb-2 pr-2">Employer</th><th className="pb-2 pr-2">Members</th><th className="pb-2">Status</th></tr></thead>
        <tbody className="divide-y divide-border">
          {employers.map((e, i) => (
            <tr key={readStr(e, "id") || i}>
              <td className="py-2 pr-2 text-foreground">{readStr(e, "name", "employerName") || "—"}</td>
              <td className="py-2 pr-2 text-muted-foreground">{readNum(e, "memberCount", "members").toLocaleString()}</td>
              <td className="py-2"><Badge status={readStr(e, "status")} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </RuvimboSection>
  );
}

function PlatformPanel() {
  return (
    <RuvimboSection title="Platform governance" icon={<ShieldAlert className="h-4 w-4 text-violet-700" />}>
      <RuvimboEmptyState
        icon={<ShieldAlert className="h-6 w-6" />}
        title="Standards, rules & integration health are being added"
        explanation="Terminology mapping, adjudication rule versions, routing configuration, integration health, data-quality reconciliation and certificate monitoring will be governed here."
        when="These surfaces depend on the rules and terminology services being wired into Ruvimbo — a tracked backend seam."
        guidance="Payer, plan and employer administration are available now under the other tabs and in the operations console."
        actions={[{ label: "Operations console", href: "/coverage/operations", variant: "secondary" }]}
      />
    </RuvimboSection>
  );
}
