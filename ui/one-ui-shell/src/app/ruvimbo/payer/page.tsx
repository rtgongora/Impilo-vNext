"use client";

/**
 * Ruvimbo Payer (/ruvimbo/payer) — the workspace through which a payer administers
 * membership, plans, authorisations, claim adjudication, employers and integrity.
 *
 * Wired to real coverage-service data via useRuvimbo. This is the canonical payer surface;
 * the deepest registry CRUD remains available in the operations console, linked from here.
 * Work-queue actions cause real state transitions (verify, decide, adjudicate, review).
 */

import { useState } from "react";
import {
  Landmark,
  UserCheck,
  ShieldCheck,
  FileText,
  Building2,
  AlertTriangle,
  Layers,
  Loader2,
  ExternalLink,
} from "lucide-react";
import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  usePayers,
  usePendingVerification,
  useVerifyMembership,
  useTransitionMembership,
  useAuthorisations,
  useAuthorisationAction,
  useFraudFlags,
  useReviewFraudFlag,
  useEmployers,
  useSchemes,
  useProducts,
  usePlanVersions,
  usePublishPlanVersion,
  useClaimV2,
  useClaimAction,
  ruvimboObject,
} from "@/hooks/queries/useRuvimbo";
import { RuvimboSection } from "@/components/ruvimbo/RuvimboSection";
import { RuvimboSummaryCard } from "@/components/ruvimbo/RuvimboSummaryCard";
import { RuvimboSubNav, type RuvimboTab } from "@/components/ruvimbo/RuvimboSubNav";
import { RuvimboEmptyState } from "@/components/ruvimbo/RuvimboEmptyState";
import { asRecord, readStr, readNum, statusTone } from "@/components/ruvimbo/util";

type TabKey = "overview" | "membership" | "plans" | "authorisations" | "claims" | "employers" | "integrity";

function Badge({ status }: { status: string }) {
  return <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${statusTone(status)}`}>{status || "—"}</span>;
}
const rows = (d: unknown) => (Array.isArray(d) ? d : []).map(asRecord);

export default function RuvimboPayerPage() {
  const [tab, setTab] = useState<TabKey>("overview");

  const pendingQ = usePendingVerification();
  const authsQ = useAuthorisations({ status: "SUBMITTED" });
  const fraudQ = useFraudFlags("OPEN");
  const payersQ = usePayers();

  const pending = rows(pendingQ.data);
  const auths = rows(authsQ.data);
  const fraud = rows(fraudQ.data);
  const payers = rows(payersQ.data);

  const tabs: RuvimboTab[] = [
    { key: "overview", label: "Overview", icon: <Landmark className="h-4 w-4" /> },
    { key: "membership", label: "Membership", icon: <UserCheck className="h-4 w-4" />, count: pending.length },
    { key: "plans", label: "Schemes & plans", icon: <Layers className="h-4 w-4" /> },
    { key: "authorisations", label: "Authorisations", icon: <ShieldCheck className="h-4 w-4" />, count: auths.length },
    { key: "claims", label: "Adjudication", icon: <FileText className="h-4 w-4" /> },
    { key: "employers", label: "Employers", icon: <Building2 className="h-4 w-4" /> },
    { key: "integrity", label: "Integrity", icon: <AlertTriangle className="h-4 w-4" />, count: fraud.length },
  ];

  return (
    <AppLayout>
      <PageShell title="Ruvimbo Payer" subtitle="Membership, benefits, adjudication, payments and appeals" serviceSlug="ruvimbo">
        <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-border bg-card p-3 text-sm shadow-sm">
          <span className="text-muted-foreground">{payers.length} payer(s) on the platform</span>
          <Link href="/coverage/operations" className="inline-flex items-center gap-1 text-xs font-medium text-violet-700 hover:text-violet-900">
            Full operations console <ExternalLink className="h-3.5 w-3.5" />
          </Link>
        </div>

        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <RuvimboSummaryCard icon={<UserCheck className="h-4 w-4" />} label="Membership verification" value={pending.length} hint="awaiting verification" tone={pending.length ? "warning" : "neutral"} isLoading={pendingQ.isLoading} onClick={() => setTab("membership")} />
          <RuvimboSummaryCard icon={<ShieldCheck className="h-4 w-4" />} label="Authorisations" value={auths.length} hint="submitted, awaiting decision" tone={auths.length ? "warning" : "neutral"} isLoading={authsQ.isLoading} onClick={() => setTab("authorisations")} />
          <RuvimboSummaryCard icon={<AlertTriangle className="h-4 w-4" />} label="Integrity flags" value={fraud.length} hint="open, awaiting review" tone={fraud.length ? "danger" : "neutral"} isLoading={fraudQ.isLoading} onClick={() => setTab("integrity")} />
          <RuvimboSummaryCard icon={<Landmark className="h-4 w-4" />} label="Payers" value={payers.length} hint="registered" tone="brand" isLoading={payersQ.isLoading} onClick={() => setTab("plans")} />
        </div>

        <RuvimboSubNav tabs={tabs} active={tab} onChange={(k) => setTab(k as TabKey)} />

        <div className="space-y-4">
          {tab === "overview" && <MembershipPanel pendingQ={pendingQ} compact />}
          {tab === "overview" && <IntegrityPanel fraudQ={fraudQ} compact />}
          {tab === "membership" && <MembershipPanel pendingQ={pendingQ} />}
          {tab === "plans" && <PlansPanel payers={payers} payersQ={payersQ} />}
          {tab === "authorisations" && <AuthDecisionPanel />}
          {tab === "claims" && <AdjudicationPanel />}
          {tab === "employers" && <EmployersPanel />}
          {tab === "integrity" && <IntegrityPanel fraudQ={fraudQ} />}
        </div>
      </PageShell>
    </AppLayout>
  );
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function MembershipPanel({ pendingQ, compact }: { pendingQ: any; compact?: boolean }) {
  const pending = rows(pendingQ.data);
  const verify = useVerifyMembership();
  const transition = useTransitionMembership();
  return (
    <RuvimboSection
      title={compact ? "Membership awaiting verification" : "Membership verification queue"}
      icon={<UserCheck className="h-4 w-4 text-violet-700" />}
      isLoading={pendingQ.isLoading}
      isError={pendingQ.isError}
      onRetry={() => pendingQ.refetch()}
      isEmpty={!pending.length}
      emptyState={<RuvimboEmptyState title="No memberships awaiting verification" explanation="Members declared as enrolled appear here until their identity and eligibility are verified." when="New declarations arrive as citizens and employers enrol." />}
    >
      <ul className="divide-y divide-border">
        {pending.slice(0, compact ? 4 : undefined).map((m, i) => {
          const id = readStr(m, "id");
          return (
            <li key={id || i} className="flex items-center justify-between gap-2 py-2 text-sm">
              <div className="min-w-0">
                <p className="truncate text-foreground">{readStr(m, "memberName", "clientId", "member_cpid") || id.slice(0, 8)}</p>
                <p className="truncate text-xs text-muted-foreground">{readStr(m, "planName", "planCode") || "—"}</p>
              </div>
              <div className="flex items-center gap-2">
                <Badge status={readStr(m, "verificationStatus", "status")} />
                {!compact && id ? (
                  <>
                    <button type="button" disabled={verify.isPending} onClick={() => verify.mutate({ id, body: { verifiedBy: "payer" } })} className="rounded-md bg-violet-600 px-2 py-1 text-xs font-medium text-white hover:bg-violet-700 disabled:opacity-50">Verify</button>
                    <button type="button" disabled={transition.isPending} onClick={() => transition.mutate({ id, action: "suspend" })} className="rounded-md border border-border px-2 py-1 text-xs font-medium hover:bg-muted disabled:opacity-50">Suspend</button>
                  </>
                ) : null}
              </div>
            </li>
          );
        })}
      </ul>
    </RuvimboSection>
  );
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function PlansPanel({ payers, payersQ }: { payers: Record<string, unknown>[]; payersQ: any }) {
  const [payerId, setPayerId] = useState("");
  const [schemeId, setSchemeId] = useState("");
  const [productId, setProductId] = useState("");
  const schemesQ = useSchemes(payerId || undefined);
  const productsQ = useProducts(schemeId || undefined);
  const versionsQ = usePlanVersions(productId || undefined);
  const publish = usePublishPlanVersion(productId);
  const schemes = rows(schemesQ.data);
  const products = rows(productsQ.data);
  const versions = rows(versionsQ.data);
  return (
    <RuvimboSection title="Schemes, products & plan versions" icon={<Layers className="h-4 w-4 text-violet-700" />} isLoading={payersQ.isLoading} isError={payersQ.isError} onRetry={() => payersQ.refetch()} isEmpty={!payers.length}
      emptyState={<RuvimboEmptyState title="No payers registered" explanation="Register a payer to build its schemes, products, plan versions and benefits." when="Payers are onboarded in administration or the operations console." actions={[{ label: "Operations console", href: "/coverage/operations" }]} />}
    >
      <div className="grid gap-2 sm:grid-cols-3">
        <select className="rounded-md border border-border bg-background px-2 py-1.5 text-sm" value={payerId} onChange={(e) => { setPayerId(e.target.value); setSchemeId(""); setProductId(""); }}>
          <option value="">Choose payer…</option>
          {payers.map((p, i) => <option key={readStr(p, "id") || i} value={readStr(p, "id")}>{readStr(p, "name", "payerName") || readStr(p, "id").slice(0, 8)}</option>)}
        </select>
        <select className="rounded-md border border-border bg-background px-2 py-1.5 text-sm" value={schemeId} onChange={(e) => { setSchemeId(e.target.value); setProductId(""); }} disabled={!payerId}>
          <option value="">Choose scheme…</option>
          {schemes.map((s, i) => <option key={readStr(s, "id") || i} value={readStr(s, "id")}>{readStr(s, "name", "schemeName") || readStr(s, "id").slice(0, 8)}</option>)}
        </select>
        <select className="rounded-md border border-border bg-background px-2 py-1.5 text-sm" value={productId} onChange={(e) => setProductId(e.target.value)} disabled={!schemeId}>
          <option value="">Choose product…</option>
          {products.map((p, i) => <option key={readStr(p, "id") || i} value={readStr(p, "id")}>{readStr(p, "name", "productName") || readStr(p, "id").slice(0, 8)}</option>)}
        </select>
      </div>
      {productId ? (
        <table className="mt-3 w-full text-sm">
          <thead><tr className="border-b text-left text-muted-foreground"><th className="pb-2 pr-2">Version</th><th className="pb-2 pr-2">Status</th><th className="pb-2">Action</th></tr></thead>
          <tbody className="divide-y divide-border">
            {versions.map((v, i) => {
              const id = readStr(v, "id");
              const st = readStr(v, "status");
              return (
                <tr key={id || i}>
                  <td className="py-2 pr-2 text-foreground">{readStr(v, "versionLabel", "version") || id.slice(0, 8)}</td>
                  <td className="py-2 pr-2"><Badge status={st} /></td>
                  <td className="py-2">{st !== "PUBLISHED" && id ? <button type="button" disabled={publish.isPending} onClick={() => publish.mutate(id)} className="rounded-md bg-violet-600 px-2 py-1 text-xs font-medium text-white hover:bg-violet-700 disabled:opacity-50">Publish</button> : <span className="text-xs text-muted-foreground">—</span>}</td>
                </tr>
              );
            })}
            {!versions.length ? <tr><td colSpan={3} className="py-3 text-sm text-muted-foreground">No plan versions for this product.</td></tr> : null}
          </tbody>
        </table>
      ) : <p className="mt-3 text-sm text-muted-foreground">Choose a payer, scheme and product to see plan versions.</p>}
    </RuvimboSection>
  );
}

function AuthDecisionPanel() {
  const [status, setStatus] = useState("ACKNOWLEDGED");
  const q = useAuthorisations({ status });
  const act = useAuthorisationAction();
  const list = rows(q.data);
  return (
    <RuvimboSection
      title="Authorisation decisions"
      icon={<ShieldCheck className="h-4 w-4 text-violet-700" />}
      actions={<select value={status} onChange={(e) => setStatus(e.target.value)} className="rounded-md border border-border bg-background px-2 py-1 text-xs">{["SUBMITTED", "ACKNOWLEDGED", "APPROVED", "DENIED"].map((s) => <option key={s} value={s}>{s}</option>)}</select>}
      isLoading={q.isLoading} isError={q.isError} onRetry={() => q.refetch()} isEmpty={!list.length}
      emptyState={<RuvimboEmptyState title={`No ${status.toLowerCase()} authorisations`} explanation="Review clinically-acknowledged requests and record an approve / partial / deny decision." when="Requests arrive from providers and move through the queue." />}
    >
      <ul className="divide-y divide-border">
        {list.map((a, i) => {
          const id = readStr(a, "id");
          const canDecide = /ACKNOWLEDGED|SUBMITTED/i.test(readStr(a, "status"));
          return (
            <li key={id || i} className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
              <span className="min-w-0 flex-1 truncate text-foreground">{readStr(a, "serviceDescription", "service", "reference") || id.slice(0, 8)}</span>
              <Badge status={readStr(a, "status")} />
              {canDecide && id ? (
                <div className="flex gap-1">
                  <button type="button" disabled={act.isPending} onClick={() => act.mutate({ id, action: "decision", body: { decision: "APPROVED" } })} className="rounded-md bg-green-600 px-2 py-1 text-xs font-medium text-white hover:bg-green-700 disabled:opacity-50">Approve</button>
                  <button type="button" disabled={act.isPending} onClick={() => act.mutate({ id, action: "decision", body: { decision: "DENIED" } })} className="rounded-md border border-red-200 px-2 py-1 text-xs font-medium text-danger hover:bg-red-50 disabled:opacity-50">Deny</button>
                </div>
              ) : null}
            </li>
          );
        })}
      </ul>
    </RuvimboSection>
  );
}

function AdjudicationPanel() {
  const [claimId, setClaimId] = useState("");
  const claimQ = useClaimV2(claimId || undefined);
  const action = useClaimAction();
  const claim = claimQ.data ? ruvimboObject(claimQ.data) : null;
  return (
    <RuvimboSection title="Claim adjudication" icon={<FileText className="h-4 w-4 text-violet-700" />} actions={<input className="w-56 rounded-lg border border-border px-3 py-1.5 text-sm" placeholder="Claim ID" value={claimId} onChange={(e) => setClaimId(e.target.value)} />}>
      {!claimId ? (
        <RuvimboEmptyState title="Adjudicate a claim" explanation="Enter a claim ID to view its submitted lines and run line-level adjudication — benefit coverage, authorisation match and copay/coinsurance split." when="Submitted claims from providers and the claims switch are adjudicated here." />
      ) : claimQ.isLoading ? <p className="text-sm text-muted-foreground">Loading claim…</p> : claimQ.isError ? <p className="text-sm text-danger">Claim not found.</p> : claim ? (
        <div className="space-y-3 text-sm">
          <div className="flex flex-wrap items-center gap-3 rounded-lg border border-border p-3">
            <Badge status={readStr(claim, "status")} />
            <span className="text-muted-foreground">Submitted <span className="text-foreground">{readNum(claim, "submittedAmount", "amount").toLocaleString()}</span></span>
            <span className="text-muted-foreground">Approved <span className="text-foreground">{readNum(claim, "approvedAmount").toLocaleString()}</span></span>
            <button type="button" disabled={action.isPending} onClick={() => action.mutate({ id: claimId, action: "adjudicate" })} className="ml-auto rounded-md bg-violet-600 px-3 py-1 text-xs font-medium text-white hover:bg-violet-700 disabled:opacity-50">{action.isPending ? <Loader2 className="mr-1 inline h-3 w-3 animate-spin" /> : null}Adjudicate</button>
          </div>
        </div>
      ) : null}
    </RuvimboSection>
  );
}

function EmployersPanel() {
  const q = useEmployers();
  const list = rows(q.data);
  return (
    <RuvimboSection title="Employers & groups" icon={<Building2 className="h-4 w-4 text-violet-700" />} isLoading={q.isLoading} isError={q.isError} onRetry={() => q.refetch()} isEmpty={!list.length}
      emptyState={<RuvimboEmptyState title="No employers registered" explanation="Employers sponsor group membership. Rosters are staged, validated and applied to create employer-sponsored memberships." when="Register employers in administration or the operations console." actions={[{ label: "Operations console", href: "/coverage/operations" }]} />}
    >
      <table className="w-full text-sm">
        <thead><tr className="border-b text-left text-muted-foreground"><th className="pb-2 pr-2">Employer</th><th className="pb-2 pr-2">Members</th><th className="pb-2">Status</th></tr></thead>
        <tbody className="divide-y divide-border">
          {list.map((e, i) => (
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

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function IntegrityPanel({ fraudQ, compact }: { fraudQ: any; compact?: boolean }) {
  const list = rows(fraudQ.data);
  const review = useReviewFraudFlag();
  return (
    <RuvimboSection
      title={compact ? "Integrity flags" : "Fraud, waste & abuse"}
      icon={<AlertTriangle className="h-4 w-4 text-violet-700" />}
      isLoading={fraudQ.isLoading} isError={fraudQ.isError} onRetry={() => fraudQ.refetch()} isEmpty={!list.length}
      emptyState={<RuvimboEmptyState title="No open integrity flags" explanation="Duplicate claims, claims after termination and improbable amounts are flagged for governed review — never auto-denied." when="Flags are raised automatically as claims are screened." />}
    >
      <ul className="divide-y divide-border">
        {list.slice(0, compact ? 4 : undefined).map((f, i) => {
          const id = readStr(f, "id");
          return (
            <li key={id || i} className="flex items-center justify-between gap-2 py-2 text-sm">
              <div className="min-w-0">
                <p className="truncate text-foreground">{readStr(f, "flagType", "type") || "Flag"}</p>
                <p className="truncate text-xs text-muted-foreground">{readStr(f, "reason", "description")}</p>
              </div>
              <div className="flex items-center gap-2">
                <Badge status={readStr(f, "status")} />
                {!compact && id ? <button type="button" disabled={review.isPending} onClick={() => review.mutate({ id, body: { disposition: "REVIEWED" } })} className="rounded-md border border-border px-2 py-1 text-xs font-medium hover:bg-muted disabled:opacity-50">Mark reviewed</button> : null}
              </div>
            </li>
          );
        })}
      </ul>
    </RuvimboSection>
  );
}
