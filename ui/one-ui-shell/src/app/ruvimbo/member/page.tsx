"use client";

/**
 * My Ruvimbo — the citizen health-financing dashboard (/ruvimbo/member).
 *
 * Answers-first: the top of the page tells the person whether they are covered, on what
 * plan, what benefits remain, and what needs their attention — then an action grid and a
 * sub-nav open the detail. Every panel is wired to real coverage-service data via the BFF
 * (useMemberCoverage + useRuvimbo); empty panels explain the feature and offer a next
 * action rather than showing a dead "No X" line. Replaces the flat /coverage/member stack.
 */

import { useMemo, useState } from "react";
import Link from "next/link";
import {
  ShieldCheck,
  ClipboardList,
  Gavel,
  PiggyBank,
  Stethoscope,
  History,
  BarChart3,
  MapPin,
  Users,
  LifeBuoy,
  Loader2,
  Plus,
  Search,
  Calculator,
  LayoutDashboard,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useMyCoveragePlans,
  useMemberAccumulators,
  useMyClaimHistory,
  useMyContributions,
  useMyUtilization,
  useMyAppeals,
  useSubmitAppeal,
  useMyCards,
} from "@/hooks/queries/useMemberCoverage";
import { useAuthorisations, useReferrals, useMemberDependants, useMemberNetworks } from "@/hooks/queries/useRuvimbo";
import { RuvimboSection } from "@/components/ruvimbo/RuvimboSection";
import { RuvimboSummaryCard } from "@/components/ruvimbo/RuvimboSummaryCard";
import { RuvimboSubNav, type RuvimboTab } from "@/components/ruvimbo/RuvimboSubNav";
import { RuvimboActionGrid, type RuvimboActionItem } from "@/components/ruvimbo/RuvimboActionGrid";
import { RuvimboEmptyState } from "@/components/ruvimbo/RuvimboEmptyState";
import { RuvimboCostEstimate } from "@/components/ruvimbo/RuvimboCostEstimate";
import {
  asRecord,
  readStr,
  readNum,
  formatMonthYear,
  formatDate,
  utilizationPct,
  utilizationBarTone,
  statusTone,
} from "@/components/ruvimbo/util";

type TabKey =
  | "overview"
  | "coverage"
  | "benefits"
  | "authorisations"
  | "claims"
  | "payments"
  | "findcare"
  | "dependants"
  | "support";

function arr(data: unknown): Record<string, unknown>[] {
  return (Array.isArray(data) ? data : []).map(asRecord);
}

function Badge({ status }: { status: string }) {
  return (
    <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${statusTone(status)}`}>
      {status || "—"}
    </span>
  );
}

export default function RuvimboMemberPage() {
  const cpid = useAuthStore((s) => s.user?.id) ?? "";
  const [tab, setTab] = useState<TabKey>("overview");
  const [appealOpen, setAppealOpen] = useState(false);
  const [appealForm, setAppealForm] = useState({ claimId: "", coverageId: "", reason: "" });

  const plansQ = useMyCoveragePlans(cpid || null);
  const plans = arr(plansQ.data);
  const firstPlan = plans[0] ?? {};
  const membershipId = readStr(firstPlan, "id");
  const planName = readStr(firstPlan, "planName", "plan_name");
  const memberNumber = readStr(firstPlan, "memberNumber", "member_number");
  const coverageStatus = readStr(firstPlan, "status") || (plans.length ? "ACTIVE" : "");

  const accumQ = useMemberAccumulators(membershipId || null);
  const utilQ = useMyUtilization(cpid || null);
  const claimsQ = useMyClaimHistory(cpid || null, 0, 10);
  const contribQ = useMyContributions(cpid || null);
  const authQ = useAuthorisations({ memberCpid: cpid || undefined });
  const refQ = useReferrals(cpid || undefined);
  const appealsQ = useMyAppeals(cpid || null);
  const depQ = useMemberDependants(membershipId || undefined);
  const networksQ = useMemberNetworks(cpid || undefined);
  const cardsQ = useMyCards();
  const submitAppeal = useSubmitAppeal();

  const accumulators = arr(accumQ.data);
  const utilization = arr(utilQ.data);
  const claims = arr(claimsQ.data);
  const contributions = arr(contribQ.data);
  const authorisations = arr(authQ.data);
  const referrals = arr(refQ.data);
  const appeals = arr(appealsQ.data);
  const dependants = arr(depQ.data);
  const cards = arr(cardsQ.data);

  const pendingAuths = useMemo(
    () => authorisations.filter((a) => /PENDING|SUBMITTED|ACK|INFO/i.test(readStr(a, "status"))).length,
    [authorisations],
  );
  const benefitsRemaining = useMemo(
    () => accumulators.reduce((sum, a) => sum + readNum(a, "remaining", "remainingAmount"), 0),
    [accumulators],
  );
  const latestClaim = claims[0];
  const actionableAppeals = appeals.filter((a) => /PENDING|UNDER_REVIEW/i.test(readStr(a, "status")));

  const isCovered = plans.length > 0;
  type NextAction = { label: string; href: string } | { label: string; key: TabKey };
  const nextAction: NextAction = !isCovered
    ? { label: "Add or find coverage", href: "/coverage/enroll" }
    : actionableAppeals.length
      ? { label: "Review an appeal", key: "support" as TabKey }
      : pendingAuths
        ? { label: "Track authorisations", key: "authorisations" as TabKey }
        : { label: "Check what's covered", key: "benefits" as TabKey };

  const tabs: RuvimboTab[] = [
    { key: "overview", label: "Overview", icon: <LayoutDashboard className="h-4 w-4" /> },
    { key: "coverage", label: "Coverage", icon: <ShieldCheck className="h-4 w-4" /> },
    { key: "benefits", label: "Benefits", icon: <BarChart3 className="h-4 w-4" /> },
    { key: "authorisations", label: "Authorisations", icon: <Stethoscope className="h-4 w-4" />, count: pendingAuths },
    { key: "claims", label: "Claims", icon: <History className="h-4 w-4" /> },
    { key: "payments", label: "Payments", icon: <PiggyBank className="h-4 w-4" /> },
    { key: "findcare", label: "Find care", icon: <MapPin className="h-4 w-4" /> },
    { key: "dependants", label: "Dependants", icon: <Users className="h-4 w-4" /> },
    { key: "support", label: "Support", icon: <LifeBuoy className="h-4 w-4" />, count: actionableAppeals.length },
  ];

  const actions: RuvimboActionItem[] = [
    { title: "Add or verify coverage", href: "/coverage/enroll", icon: <Plus className="h-4 w-4" />, access: "open" },
    { title: "Check what's covered", onClick: () => setTab("benefits"), icon: <Search className="h-4 w-4" />, access: "open" },
    { title: "Find covered care", href: "/welcome/find-care", icon: <MapPin className="h-4 w-4" />, access: "open" },
    { title: "Track a claim", onClick: () => setTab("claims"), icon: <History className="h-4 w-4" />, access: "open" },
    { title: "Manage dependants", onClick: () => setTab("dependants"), icon: <Users className="h-4 w-4" />, access: "open" },
    { title: "Appeal a decision", onClick: () => setTab("support"), icon: <Gavel className="h-4 w-4" />, access: "open" },
    { title: "Estimate my cost", onClick: () => setTab("benefits"), icon: <Calculator className="h-4 w-4" />, access: "open" },
    { title: "Digital member card", onClick: () => setTab("coverage"), icon: <ShieldCheck className="h-4 w-4" />, access: "open" },
  ];

  function sendAppeal() {
    if (!appealForm.claimId || !appealForm.reason) return;
    submitAppeal.mutate(
      {
        claimId: appealForm.claimId,
        coverageId: appealForm.coverageId || undefined,
        appellantType: "MEMBER",
        appellantId: cpid,
        reason: appealForm.reason,
      },
      {
        onSuccess: () => {
          setAppealOpen(false);
          setAppealForm({ claimId: "", coverageId: "", reason: "" });
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell title="My Ruvimbo" subtitle="Your coverage, benefits, claims and financial support in one place" serviceSlug="ruvimbo">
        {/* ── Coverage status strip ── */}
        <div className="grid gap-3 rounded-xl border border-border bg-card p-4 shadow-sm sm:grid-cols-4">
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Coverage status</p>
            <p className="mt-1 text-sm font-semibold text-foreground">
              {plansQ.isLoading ? "Checking…" : isCovered ? <Badge status={coverageStatus} /> : "Not yet linked"}
            </p>
          </div>
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Primary plan</p>
            <p className="mt-1 truncate text-sm font-semibold text-foreground">{planName || "—"}</p>
          </div>
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Member number</p>
            <p className="mt-1 font-mono text-sm text-foreground">{memberNumber || "—"}</p>
          </div>
          <div className="flex items-end">
            {"href" in nextAction ? (
              <Link
                href={nextAction.href}
                className="w-full rounded-lg bg-violet-600 px-3 py-2 text-center text-sm font-semibold text-white hover:bg-violet-700"
              >
                {nextAction.label}
              </Link>
            ) : (
              <button
                type="button"
                onClick={() => setTab(nextAction.key)}
                className="w-full rounded-lg bg-violet-600 px-3 py-2 text-sm font-semibold text-white hover:bg-violet-700"
              >
                {nextAction.label}
              </button>
            )}
          </div>
        </div>

        {/* ── Summary cards ── */}
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <RuvimboSummaryCard
            icon={<ShieldCheck className="h-4 w-4" />}
            label="My coverage"
            value={plans.length}
            hint={isCovered ? "active plan(s)" : "No coverage linked"}
            tone={isCovered ? "positive" : "warning"}
            isLoading={plansQ.isLoading}
            onClick={() => setTab("coverage")}
          />
          <RuvimboSummaryCard
            icon={<BarChart3 className="h-4 w-4" />}
            label="Benefits remaining"
            value={accumulators.length ? benefitsRemaining.toLocaleString() : "—"}
            hint={accumulators.length ? "across tracked benefits" : "No real-time balances"}
            tone="brand"
            isLoading={accumQ.isLoading}
            onClick={() => setTab("benefits")}
          />
          <RuvimboSummaryCard
            icon={<History className="h-4 w-4" />}
            label="Claims"
            value={claims.length}
            hint={latestClaim ? `latest: ${readStr(latestClaim, "status") || "—"}` : "No claims yet"}
            tone="neutral"
            isLoading={claimsQ.isLoading}
            onClick={() => setTab("claims")}
          />
          <RuvimboSummaryCard
            icon={<Stethoscope className="h-4 w-4" />}
            label="Authorisations"
            value={pendingAuths}
            hint={pendingAuths ? "awaiting a decision" : "none pending"}
            tone={pendingAuths ? "warning" : "neutral"}
            isLoading={authQ.isLoading}
            onClick={() => setTab("authorisations")}
          />
        </div>

        {/* ── Action grid ── */}
        <RuvimboActionGrid actions={actions} />

        {/* ── Sub-navigation ── */}
        <RuvimboSubNav tabs={tabs} active={tab} onChange={(k) => setTab(k as TabKey)} />

        {/* ── Tab content ── */}
        <div className="space-y-4">
          {(tab === "overview" || tab === "coverage") && (
            <RuvimboSection
              title="My plans"
              icon={<ShieldCheck className="h-4 w-4 text-violet-700" />}
              isLoading={plansQ.isLoading}
              isError={plansQ.isError}
              onRetry={() => plansQ.refetch()}
              isEmpty={!plans.length}
              emptyState={
                <RuvimboEmptyState
                  title="No coverage has been linked yet"
                  explanation="Add a medical aid, insurer, employer scheme, Government programme or other financial support to see your plans, benefits and member card here."
                  when="Once linked, your plan, member number and benefits appear automatically."
                  actions={[
                    { label: "Add coverage", href: "/coverage/enroll" },
                    { label: "Search for my coverage", href: "/coverage/enroll", variant: "secondary" },
                  ]}
                  guidance="Nompilo can help you find a participating payer using your details."
                />
              }
            >
              <div className="grid gap-3 sm:grid-cols-2" data-testid="coverage-member-plans-table">
                {plans.map((p, i) => (
                  <div key={readStr(p, "id") || i} className="rounded-lg border border-border p-3">
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <p className="text-sm font-semibold text-foreground">{readStr(p, "planName", "plan_name") || "Plan"}</p>
                        <p className="text-xs text-muted-foreground">{readStr(p, "planCode", "plan_code")}</p>
                      </div>
                      <Badge status={readStr(p, "status") || "ACTIVE"} />
                    </div>
                    <dl className="mt-2 grid grid-cols-2 gap-1 text-xs text-muted-foreground">
                      <div>Member no. <span className="font-mono text-foreground">{readStr(p, "memberNumber", "member_number") || "—"}</span></div>
                      <div>Relationship <span className="text-foreground">{readStr(p, "relationship") || "SELF"}</span></div>
                    </dl>
                  </div>
                ))}
              </div>
            </RuvimboSection>
          )}

          {tab === "coverage" && (
            <RuvimboSection
              title="Digital member card"
              icon={<ShieldCheck className="h-4 w-4 text-violet-700" />}
              isLoading={cardsQ.isLoading}
              isError={cardsQ.isError}
              onRetry={() => cardsQ.refetch()}
              isEmpty={!cards.length}
              emptyState={
                <RuvimboEmptyState
                  title="No digital card yet"
                  explanation="Your health/member card lets you present your coverage at a facility. It appears here once issued."
                  when="A card is issued after your membership is verified."
                />
              }
            >
              <div className="grid gap-3 sm:grid-cols-2">
                {cards.map((c, i) => (
                  <div key={readStr(c, "id") || i} className="rounded-xl bg-gradient-to-br from-violet-600 to-indigo-700 p-4 text-white shadow">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-medium uppercase tracking-wide text-white/80">{readStr(c, "cardType", "type") || "Member card"}</span>
                      <Badge status={readStr(c, "status") || "ACTIVE"} />
                    </div>
                    <p className="mt-4 font-mono text-lg tracking-widest">
                      {(readStr(c, "cardNumber", "maskedNumber", "pan") || "•••• •••• ••••").replace(/.(?=.{4})/g, "•")}
                    </p>
                    <p className="mt-2 text-sm text-white/90">{planName || readStr(c, "holderName", "name") || "—"}</p>
                  </div>
                ))}
              </div>
            </RuvimboSection>
          )}

          {tab === "overview" && (
            <RuvimboSection
              title="Appeals & attention"
              icon={<Gavel className="h-4 w-4 text-violet-700" />}
              isLoading={appealsQ.isLoading}
              isError={appealsQ.isError}
              onRetry={() => appealsQ.refetch()}
              isEmpty={!appeals.length}
              emptyState={
                <RuvimboEmptyState
                  title="Nothing needs your attention"
                  explanation="Appealable claim or authorisation decisions, and items awaiting information, will surface here as alerts."
                  when="You have not filed any appeals, and no decisions are currently awaiting action."
                  actions={[{ label: "How appeals work", onClick: () => setTab("support"), variant: "secondary" }]}
                />
              }
            >
              <ul className="space-y-2">
                {appeals.slice(0, 4).map((a, i) => (
                  <li key={readStr(a, "id") || i} className="flex items-center justify-between gap-2 rounded-lg border border-border px-3 py-2 text-sm">
                    <span className="truncate text-foreground">{readStr(a, "reason") || "Appeal"}</span>
                    <Badge status={readStr(a, "status")} />
                  </li>
                ))}
              </ul>
            </RuvimboSection>
          )}

          {tab === "benefits" && (
            <RuvimboSection
              title="Benefit balances & utilisation"
              icon={<BarChart3 className="h-4 w-4 text-violet-700" />}
              isLoading={accumQ.isLoading || utilQ.isLoading}
              isError={accumQ.isError}
              onRetry={() => accumQ.refetch()}
              isEmpty={!accumulators.length && !utilization.length}
              emptyState={
                <RuvimboEmptyState
                  title="No benefit balances yet"
                  explanation="When your plan exposes real-time balances, each benefit category shows what you have used, reserved and have remaining."
                  when="Some payers do not provide real-time balances — you can still check whether a specific service is covered."
                  actions={[{ label: "Add coverage", href: "/coverage/enroll" }]}
                />
              }
            >
              <div className="space-y-3">
                {accumulators.map((b, i) => {
                  const limit = readNum(b, "limit", "limitAmount");
                  const used = readNum(b, "used", "usedAmount");
                  const reserved = readNum(b, "reserved", "reservedAmount");
                  const remaining = readNum(b, "remaining", "remainingAmount");
                  const pct = utilizationPct(used + reserved, limit);
                  return (
                    <div key={readStr(b, "benefitCode", "id") || i} className="rounded-lg border border-border p-3">
                      <div className="flex items-center justify-between text-sm">
                        <span className="font-medium text-foreground">{readStr(b, "benefitName", "benefitCode", "benefit_code") || "Benefit"}</span>
                        <span className="text-muted-foreground">
                          {remaining.toLocaleString()} left{limit ? ` / ${limit.toLocaleString()}` : ""}
                        </span>
                      </div>
                      {pct !== null && (
                        <div className="mt-2 h-2 overflow-hidden rounded-full bg-muted">
                          <div className={`h-full ${utilizationBarTone(pct)}`} style={{ width: `${pct}%` }} />
                        </div>
                      )}
                      <p className="mt-1 text-xs text-muted-foreground">
                        Used {used.toLocaleString()} · Reserved {reserved.toLocaleString()}
                      </p>
                    </div>
                  );
                })}
              </div>
            </RuvimboSection>
          )}

          {tab === "benefits" && <RuvimboCostEstimate defaultCpid={cpid || undefined} />}

          {tab === "authorisations" && (
            <>
              <RuvimboSection
                title="Authorisations"
                icon={<Stethoscope className="h-4 w-4 text-violet-700" />}
                isLoading={authQ.isLoading}
                isError={authQ.isError}
                onRetry={() => authQ.refetch()}
                isEmpty={!authorisations.length}
                emptyState={
                  <RuvimboEmptyState
                    title="No authorisation requests are active"
                    explanation="Some admissions, procedures, medicines and investigations require approval before they are provided."
                    when="Requests raised for you by a facility appear here with their status and validity."
                    actions={[{ label: "Check if a service needs approval", onClick: () => setTab("benefits"), variant: "secondary" }]}
                  />
                }
              >
                <ul className="divide-y divide-border">
                  {authorisations.map((a, i) => (
                    <li key={readStr(a, "id") || i} className="flex items-center justify-between gap-2 py-2 text-sm">
                      <span className="truncate text-foreground">{readStr(a, "serviceDescription", "service", "reference") || "Authorisation"}</span>
                      <Badge status={readStr(a, "status")} />
                    </li>
                  ))}
                </ul>
              </RuvimboSection>
              <RuvimboSection
                title="Referrals"
                icon={<Stethoscope className="h-4 w-4 text-violet-700" />}
                isLoading={refQ.isLoading}
                isError={refQ.isError}
                onRetry={() => refQ.refetch()}
                isEmpty={!referrals.length}
                emptyState={
                  <RuvimboEmptyState
                    title="No referrals on file"
                    explanation="Referrals from your provider to another facility or specialist appear here, with visits allowed and used."
                    when="A referral shows up once a provider raises one for you."
                  />
                }
              >
                <ul className="divide-y divide-border">
                  {referrals.map((r, i) => (
                    <li key={readStr(r, "id") || i} className="flex items-center justify-between gap-2 py-2 text-sm">
                      <span className="truncate text-foreground">{readStr(r, "destination", "service") || "Referral"}</span>
                      <Badge status={readStr(r, "status")} />
                    </li>
                  ))}
                </ul>
              </RuvimboSection>
            </>
          )}

          {tab === "claims" && (
            <RuvimboSection
              title="Claims"
              icon={<History className="h-4 w-4 text-violet-700" />}
              isLoading={claimsQ.isLoading}
              isError={claimsQ.isError}
              onRetry={() => claimsQ.refetch()}
              isEmpty={!claims.length}
              emptyState={
                <RuvimboEmptyState
                  title="You have not submitted any claims yet"
                  explanation="Claims submitted by participating facilities appear here automatically. You can also submit a reimbursement claim for eligible services you paid for yourself."
                  when="Submitted → Received → Under review → Approved → Paid."
                  actions={[{ label: "Learn how claims work", onClick: () => setTab("support"), variant: "secondary" }]}
                />
              }
            >
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left text-muted-foreground">
                    <th className="pb-2 pr-2">Claim</th>
                    <th className="pb-2 pr-2">Submitted</th>
                    <th className="pb-2 pr-2">Approved</th>
                    <th className="pb-2">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {claims.map((c, i) => (
                    <tr key={readStr(c, "id") || i}>
                      <td className="py-2 pr-2 font-mono text-xs">{readStr(c, "claimNumber", "id").slice(0, 10) || "—"}</td>
                      <td className="py-2 pr-2">{readNum(c, "submittedAmount", "amount").toLocaleString()}</td>
                      <td className="py-2 pr-2">{readNum(c, "approvedAmount").toLocaleString()}</td>
                      <td className="py-2"><Badge status={readStr(c, "status")} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </RuvimboSection>
          )}

          {tab === "payments" && (
            <RuvimboSection
              title="Contributions & payments"
              icon={<PiggyBank className="h-4 w-4 text-violet-700" />}
              isLoading={contribQ.isLoading}
              isError={contribQ.isError}
              onRetry={() => contribQ.refetch()}
              isEmpty={!contributions.length}
              emptyState={
                <RuvimboEmptyState
                  title="No contribution records"
                  explanation="Premium and contribution history — employer and member portions, arrears and receipts — appears here. Payments are moved securely by MUSHEX."
                  when="Records appear once your payer or employer reports contributions."
                />
              }
            >
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-left text-muted-foreground">
                    <th className="pb-2 pr-2">Period</th>
                    <th className="pb-2 pr-2">Amount</th>
                    <th className="pb-2">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {contributions.map((c, i) => (
                    <tr key={readStr(c, "id") || i}>
                      <td className="py-2 pr-2">{formatMonthYear(readStr(c, "period", "periodStart", "created_at"))}</td>
                      <td className="py-2 pr-2">{readNum(c, "amount").toLocaleString()}</td>
                      <td className="py-2"><Badge status={readStr(c, "status")} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </RuvimboSection>
          )}

          {tab === "findcare" && (
            <RuvimboSection
              title="Find covered care"
              icon={<MapPin className="h-4 w-4 text-violet-700" />}
              isLoading={networksQ.isLoading}
              isError={networksQ.isError}
              onRetry={() => networksQ.refetch()}
            >
              {(() => {
                const nd = asRecord(networksQ.data);
                const networks = arr(nd.networks);
                const inNet = Array.isArray(nd.inNetworkProviderIds) ? (nd.inNetworkProviderIds as unknown[]) : [];
                if (!networks.length) {
                  return (
                    <RuvimboEmptyState
                      icon={<MapPin className="h-6 w-6" />}
                      title="Search the national facility directory"
                      explanation="Your payer has not published a provider network, so all directory facilities are open to you. Find facilities, providers, pharmacies and laboratories in the national directory."
                      when="When your payer publishes a network, your in-network providers will be highlighted here."
                      actions={[{ label: "Open facility directory", href: "/welcome/find-care" }]}
                    />
                  );
                }
                return (
                  <div className="space-y-3">
                    <p className="text-sm text-muted-foreground">
                      Your plan covers <span className="font-semibold text-foreground">{inNet.length}</span> in-network provider(s) across{" "}
                      <span className="font-semibold text-foreground">{networks.length}</span> network(s). Using an in-network provider minimises what you pay.
                    </p>
                    <ul className="divide-y divide-border">
                      {networks.map((n, i) => (
                        <li key={readStr(n, "networkId") || i} className="flex items-center justify-between gap-2 py-2 text-sm">
                          <span className="text-foreground">{readStr(n, "networkType") || "Network"}</span>
                          <span className="text-muted-foreground">
                            {Array.isArray((n as { providerIds?: unknown }).providerIds) ? ((n as { providerIds: unknown[] }).providerIds).length : 0} providers
                          </span>
                        </li>
                      ))}
                    </ul>
                    <Link href="/welcome/find-care" className="inline-block rounded-lg bg-violet-600 px-3 py-2 text-sm font-semibold text-white hover:bg-violet-700">
                      Search the facility directory
                    </Link>
                  </div>
                );
              })()}
            </RuvimboSection>
          )}

          {tab === "dependants" && (
            <RuvimboSection
              title="Dependants & household"
              icon={<Users className="h-4 w-4 text-violet-700" />}
              isLoading={depQ.isLoading}
              isError={depQ.isError}
              onRetry={() => depQ.refetch()}
              isEmpty={!dependants.length}
              emptyState={
                <RuvimboEmptyState
                  title={membershipId ? "No dependants on your membership" : "Link coverage to manage dependants"}
                  explanation="Principal and dependant coverage, verification state and effective dates appear here. You can add dependants to an active membership."
                  when={membershipId ? "Add a dependant to see their coverage and benefits." : "Dependant management becomes available once you have an active membership."}
                  actions={membershipId ? [{ label: "Add dependant", href: "/coverage/enroll", variant: "secondary" }] : [{ label: "Add coverage", href: "/coverage/enroll" }]}
                />
              }
            >
              <ul className="divide-y divide-border">
                {dependants.map((d, i) => (
                  <li key={readStr(d, "id") || i} className="flex items-center justify-between gap-2 py-2 text-sm">
                    <span className="text-foreground">{readStr(d, "fullName", "name") || "Dependant"}</span>
                    <span className="text-xs text-muted-foreground">{formatDate(readStr(d, "effectiveFrom", "effective_from"))}</span>
                    <Badge status={readStr(d, "verificationStatus", "status")} />
                  </li>
                ))}
              </ul>
            </RuvimboSection>
          )}

          {tab === "support" && (
            <RuvimboSection
              title="Appeals & support"
              icon={<Gavel className="h-4 w-4 text-violet-700" />}
              actions={
                <button
                  type="button"
                  onClick={() => setAppealOpen(!appealOpen)}
                  className="inline-flex items-center gap-1.5 rounded-lg border border-violet-300 bg-violet-50 px-3 py-1.5 text-sm font-medium text-violet-800 hover:bg-violet-100"
                >
                  <ClipboardList className="h-4 w-4" />
                  {appealOpen ? "Cancel" : "Submit appeal"}
                </button>
              }
              isLoading={appealsQ.isLoading}
              isError={appealsQ.isError}
              onRetry={() => appealsQ.refetch()}
            >
              {appealOpen && (
                <div className="mb-4 space-y-2 rounded-lg border border-border bg-background p-4">
                  <input
                    className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                    placeholder="Claim ID *"
                    value={appealForm.claimId}
                    onChange={(e) => setAppealForm({ ...appealForm, claimId: e.target.value })}
                  />
                  <textarea
                    className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                    rows={3}
                    placeholder="Reason *"
                    value={appealForm.reason}
                    onChange={(e) => setAppealForm({ ...appealForm, reason: e.target.value })}
                  />
                  <button
                    type="button"
                    disabled={submitAppeal.isPending}
                    onClick={sendAppeal}
                    className="rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700 disabled:opacity-50"
                  >
                    {submitAppeal.isPending ? <Loader2 className="mr-1 inline h-4 w-4 animate-spin" /> : null}
                    Submit
                  </button>
                  {submitAppeal.isError && <p className="text-xs text-danger">Could not submit appeal.</p>}
                </div>
              )}
              {appeals.length === 0 ? (
                <RuvimboEmptyState
                  title="No appeals filed"
                  explanation="If a claim is declined or partially paid, or an authorisation is denied, you can appeal the decision and track the outcome here."
                  when="You have 21 days from a decision to appeal, in most cases."
                  guidance="Complex or safety-related concerns can be escalated to RITO."
                />
              ) : (
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b text-left text-muted-foreground">
                      <th className="pb-2 pr-2">Claim</th>
                      <th className="pb-2 pr-2">Reason</th>
                      <th className="pb-2 pr-2">Status</th>
                      <th className="pb-2">Decision</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {appeals.map((a, i) => (
                      <tr key={readStr(a, "id") || i}>
                        <td className="py-2 pr-2 font-mono text-xs">{readStr(a, "claimId", "claim_id").slice(0, 10)}</td>
                        <td className="max-w-xs truncate py-2 pr-2 text-foreground">{readStr(a, "reason") || "—"}</td>
                        <td className="py-2 pr-2"><Badge status={readStr(a, "status")} /></td>
                        <td className="py-2 text-foreground">{readStr(a, "decision") || "—"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </RuvimboSection>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
