"use client";

/**
 * Ruvimbo Provider (/ruvimbo/provider) — the operational workspace through which a
 * facility verifies coverage, obtains authorisations, submits and tracks claims,
 * manages referrals and patient estimates, and reviews its contracts and capitation.
 *
 * Wired to real coverage-service data via the BFF (useRuvimbo + useProviderContracts).
 * Queues are trust-scoped server-side by the facility/provider headers the api-client
 * injects; forms drive the real eligibility-v2, authorisation, claim-v2 and estimate seams.
 */

import { useMemo, useState } from "react";
import {
  Stethoscope,
  ShieldCheck,
  FileText,
  Network,
  Coins,
  Search,
  Loader2,
  Calculator,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import {
  useAuthorisations,
  useAuthorisationAction,
  useReferrals,
  useLiabilityEstimates,
  useCreateLiabilityEstimate,
  useCheckEligibilityV2,
  useClaimV2,
  useClaimEob,
  useClaimEvents,
  useClaimAction,
  useCapitationReports,
  ruvimboObject,
} from "@/hooks/queries/useRuvimbo";
import { useProviderContracts } from "@/hooks/queries/useProviderContracts";
import { RuvimboSection } from "@/components/ruvimbo/RuvimboSection";
import { RuvimboSummaryCard } from "@/components/ruvimbo/RuvimboSummaryCard";
import { RuvimboSubNav, type RuvimboTab } from "@/components/ruvimbo/RuvimboSubNav";
import { RuvimboEmptyState } from "@/components/ruvimbo/RuvimboEmptyState";
import { asRecord, readStr, readNum, statusTone } from "@/components/ruvimbo/util";

type TabKey = "overview" | "eligibility" | "authorisations" | "referrals" | "claims" | "estimates" | "contracts";

function Badge({ status }: { status: string }) {
  return <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-medium ${statusTone(status)}`}>{status || "—"}</span>;
}
function field(v: string, set: (s: string) => void, ph: string) {
  return (
    <input
      className="w-full rounded-lg border border-border px-3 py-2 text-sm"
      placeholder={ph}
      value={v}
      onChange={(e) => set(e.target.value)}
    />
  );
}

export default function RuvimboProviderPage() {
  const user = useAuthStore((s) => s.user);
  const [tab, setTab] = useState<TabKey>("overview");

  // Queues (trust-scoped server-side).
  const pendingAuthsQ = useAuthorisations({ status: "SUBMITTED" });
  const contractsQ = useProviderContracts(user?.providerId ? { provider_id: user.providerId } : undefined);
  const capitationQ = useCapitationReports(user?.providerId || undefined);

  const pendingAuths = (pendingAuthsQ.data ?? []).map(asRecord);
  const contracts = (contractsQ.data ?? []).map(asRecord);
  const capitation = (capitationQ.data ?? []).map(asRecord);

  const tabs: RuvimboTab[] = [
    { key: "overview", label: "Overview", icon: <Stethoscope className="h-4 w-4" /> },
    { key: "eligibility", label: "Eligibility", icon: <Search className="h-4 w-4" /> },
    { key: "authorisations", label: "Authorisations", icon: <ShieldCheck className="h-4 w-4" />, count: pendingAuths.length },
    { key: "referrals", label: "Referrals", icon: <Stethoscope className="h-4 w-4" /> },
    { key: "claims", label: "Claims", icon: <FileText className="h-4 w-4" /> },
    { key: "estimates", label: "Estimates", icon: <Calculator className="h-4 w-4" /> },
    { key: "contracts", label: "Contracts", icon: <Network className="h-4 w-4" /> },
  ];

  return (
    <AppLayout>
      <PageShell title="Ruvimbo Provider" subtitle="Eligibility, authorisations, claims and settlement" serviceSlug="ruvimbo">
        <div className="rounded-xl border border-border bg-card p-3 text-sm shadow-sm">
          <span className="text-muted-foreground">Working as</span>{" "}
          <span className="font-semibold text-foreground">{user?.displayName || "Provider"}</span>
          {user?.providerId ? <span className="text-muted-foreground"> · Provider {user.providerId}</span> : null}
        </div>

        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <RuvimboSummaryCard icon={<ShieldCheck className="h-4 w-4" />} label="Authorisations awaiting" value={pendingAuths.length} hint="submitted, awaiting decision" tone={pendingAuths.length ? "warning" : "neutral"} isLoading={pendingAuthsQ.isLoading} onClick={() => setTab("authorisations")} />
          <RuvimboSummaryCard icon={<Network className="h-4 w-4" />} label="Contracts" value={contracts.length} hint="payer contracts" tone="brand" isLoading={contractsQ.isLoading} onClick={() => setTab("contracts")} />
          <RuvimboSummaryCard icon={<Coins className="h-4 w-4" />} label="Capitation reports" value={capitation.length} hint="periods reported" isLoading={capitationQ.isLoading} />
          <RuvimboSummaryCard icon={<FileText className="h-4 w-4" />} label="Claims" value={<span className="text-base font-medium">Console</span>} hint="create · scrub · submit · track" onClick={() => setTab("claims")} />
        </div>

        <RuvimboSubNav tabs={tabs} active={tab} onChange={(k) => setTab(k as TabKey)} />

        <div className="space-y-4">
          {tab === "overview" && (
            <RuvimboSection
              title="Authorisations awaiting decision"
              icon={<ShieldCheck className="h-4 w-4 text-violet-700" />}
              isLoading={pendingAuthsQ.isLoading}
              isError={pendingAuthsQ.isError}
              onRetry={() => pendingAuthsQ.refetch()}
              isEmpty={!pendingAuths.length}
              emptyState={<RuvimboEmptyState title="No authorisations awaiting a payer decision" explanation="Requests you submit for admissions, procedures, medicines or investigations appear here until the payer responds." when="Raise a request under Authorisations." actions={[{ label: "Request authorisation", onClick: () => setTab("authorisations") }]} />}
            >
              <ul className="divide-y divide-border">
                {pendingAuths.map((a, i) => (
                  <li key={readStr(a, "id") || i} className="flex items-center justify-between gap-2 py-2 text-sm">
                    <span className="truncate text-foreground">{readStr(a, "serviceDescription", "service", "reference") || "Authorisation"}</span>
                    <Badge status={readStr(a, "status")} />
                  </li>
                ))}
              </ul>
            </RuvimboSection>
          )}

          {tab === "eligibility" && <EligibilityPanel />}
          {tab === "authorisations" && <AuthorisationsPanel />}
          {tab === "referrals" && <ReferralsPanel />}
          {tab === "claims" && <ClaimConsolePanel providerId={user?.providerId} />}
          {tab === "estimates" && <EstimatesPanel />}
          {tab === "contracts" && (
            <RuvimboSection
              title="Payer contracts"
              icon={<Network className="h-4 w-4 text-violet-700" />}
              isLoading={contractsQ.isLoading}
              isError={contractsQ.isError}
              onRetry={() => contractsQ.refetch()}
              isEmpty={!contracts.length}
              emptyState={<RuvimboEmptyState title="No payer contracts on file" explanation="Contracts link this provider to payers and networks, setting the terms claims are paid under." when="Contracts are set up by payers and the platform administration." />}
            >
              <table className="w-full text-sm">
                <thead><tr className="border-b text-left text-muted-foreground"><th className="pb-2 pr-2">Payer</th><th className="pb-2 pr-2">Network</th><th className="pb-2">Status</th></tr></thead>
                <tbody className="divide-y divide-border">
                  {contracts.map((c, i) => (
                    <tr key={readStr(c, "id") || i}>
                      <td className="py-2 pr-2 text-foreground">{readStr(c, "payerName", "payerId", "payer_ref") || "—"}</td>
                      <td className="py-2 pr-2 text-muted-foreground">{readStr(c, "networkName", "networkId") || "—"}</td>
                      <td className="py-2"><Badge status={readStr(c, "status")} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </RuvimboSection>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}

function EligibilityPanel() {
  const [cpid, setCpid] = useState("");
  const [service, setService] = useState("");
  const check = useCheckEligibilityV2();
  const result = check.data ? ruvimboObject(check.data) : null;
  return (
    <RuvimboSection title="Check eligibility" icon={<Search className="h-4 w-4 text-violet-700" />}>
      <div className="grid gap-2 sm:grid-cols-3">
        {field(cpid, setCpid, "Member CPID *")}
        {field(service, setService, "Service code (optional)")}
        <button type="button" disabled={!cpid || check.isPending} onClick={() => check.mutate({ member_cpid: cpid, service_code: service || undefined })} className="rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700 disabled:opacity-50">
          {check.isPending ? <Loader2 className="mr-1 inline h-4 w-4 animate-spin" /> : null}Check
        </button>
      </div>
      {check.isError && <p className="mt-3 text-sm text-danger">Eligibility check failed.</p>}
      {result && (
        <div className="mt-3 rounded-lg border border-border p-3 text-sm">
          <p className="font-semibold text-foreground">{readStr(result, "resultCode", "status") || "Result"}</p>
          <p className="text-muted-foreground">{readStr(result, "resultMessage", "reason")}</p>
          {Array.isArray((result as { reasonCodes?: unknown }).reasonCodes) ? (
            <p className="mt-1 text-xs text-muted-foreground">Reason codes: {((result as { reasonCodes: string[] }).reasonCodes).join(", ")}</p>
          ) : null}
        </div>
      )}
    </RuvimboSection>
  );
}

function AuthorisationsPanel() {
  const [status, setStatus] = useState("SUBMITTED");
  const q = useAuthorisations({ status });
  const rows = (q.data ?? []).map(asRecord);
  const act = useAuthorisationAction();
  const STATUSES = ["SUBMITTED", "ACKNOWLEDGED", "APPROVED", "PARTIALLY_APPROVED", "DENIED"];
  return (
    <RuvimboSection
      title="Authorisations"
      icon={<ShieldCheck className="h-4 w-4 text-violet-700" />}
      actions={
        <select value={status} onChange={(e) => setStatus(e.target.value)} className="rounded-md border border-border bg-background px-2 py-1 text-xs">
          {STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
      }
      isLoading={q.isLoading}
      isError={q.isError}
      onRetry={() => q.refetch()}
      isEmpty={!rows.length}
      emptyState={<RuvimboEmptyState title={`No ${status.toLowerCase()} authorisations`} explanation="Authorisation requests move through submitted → acknowledged → decided. Filter by status to work each stage." when="New requests appear as facilities raise them." />}
    >
      <ul className="divide-y divide-border">
        {rows.map((a, i) => {
          const id = readStr(a, "id");
          return (
            <li key={id || i} className="flex items-center justify-between gap-2 py-2 text-sm">
              <span className="truncate text-foreground">{readStr(a, "serviceDescription", "service", "reference") || id.slice(0, 8)}</span>
              <div className="flex items-center gap-2">
                <Badge status={readStr(a, "status")} />
                {status === "SUBMITTED" && id ? (
                  <button type="button" disabled={act.isPending} onClick={() => act.mutate({ id, action: "acknowledge" })} className="rounded-md border border-border px-2 py-1 text-xs font-medium text-foreground hover:bg-muted disabled:opacity-50">Acknowledge</button>
                ) : null}
              </div>
            </li>
          );
        })}
      </ul>
    </RuvimboSection>
  );
}

function ReferralsPanel() {
  const [cpid, setCpid] = useState("");
  const q = useReferrals(cpid || undefined);
  const rows = (q.data ?? []).map(asRecord);
  return (
    <RuvimboSection title="Referrals" icon={<Stethoscope className="h-4 w-4 text-violet-700" />} actions={field(cpid, setCpid, "Member CPID")} isLoading={q.isLoading} isError={q.isError} onRetry={() => q.refetch()} isEmpty={Boolean(cpid) && !rows.length}
      emptyState={<RuvimboEmptyState title="No referrals for this member" explanation="Referrals link a patient from this provider to another facility or specialist, with visits allowed and used." when="Enter a member CPID to look up their referrals." />}
    >
      {!cpid ? <p className="text-sm text-muted-foreground">Enter a member CPID to view referrals.</p> : (
        <ul className="divide-y divide-border">
          {rows.map((r, i) => (
            <li key={readStr(r, "id") || i} className="flex items-center justify-between gap-2 py-2 text-sm">
              <span className="truncate text-foreground">{readStr(r, "destination", "service") || "Referral"}</span>
              <Badge status={readStr(r, "status")} />
            </li>
          ))}
        </ul>
      )}
    </RuvimboSection>
  );
}

function ClaimConsolePanel({ providerId }: { providerId?: string }) {
  const [claimId, setClaimId] = useState("");
  const claimQ = useClaimV2(claimId || undefined);
  const eobQ = useClaimEob(claimId || undefined);
  const eventsQ = useClaimEvents(claimId || undefined);
  const action = useClaimAction();
  const claim = claimQ.data ? ruvimboObject(claimQ.data) : null;
  const eob = eobQ.data ? ruvimboObject(eobQ.data) : null;
  const events = (eventsQ.data ?? []).map(asRecord);
  return (
    <RuvimboSection title="Claim console" icon={<FileText className="h-4 w-4 text-violet-700" />} actions={field(claimId, setClaimId, "Claim ID")}>
      {!claimId ? (
        <RuvimboEmptyState
          title="Track or submit a claim"
          explanation="Enter a claim ID to view its adjudication status, line detail, explanation of benefits and event history, and to scrub or submit it."
          when="Claims created by this facility can be entered here; new claims are created from an encounter's billing."
          guidance={providerId ? undefined : "Activate your provider identity to create claims."}
        />
      ) : (
        <div className="space-y-3 text-sm">
          {claimQ.isLoading ? <p className="text-muted-foreground">Loading claim…</p> : claimQ.isError ? <p className="text-danger">Claim not found.</p> : claim ? (
            <>
              <div className="flex flex-wrap items-center gap-3 rounded-lg border border-border p-3">
                <Badge status={readStr(claim, "status")} />
                <span className="text-muted-foreground">Submitted <span className="text-foreground">{readNum(claim, "submittedAmount", "amount").toLocaleString()}</span></span>
                <span className="text-muted-foreground">Approved <span className="text-foreground">{readNum(claim, "approvedAmount").toLocaleString()}</span></span>
                <span className="text-muted-foreground">Patient <span className="text-foreground">{readNum(claim, "patientResponsibility").toLocaleString()}</span></span>
                <div className="ml-auto flex gap-2">
                  <button type="button" disabled={action.isPending} onClick={() => action.mutate({ id: claimId, action: "scrub" })} className="rounded-md border border-border px-2 py-1 text-xs font-medium hover:bg-muted disabled:opacity-50">Scrub</button>
                  <button type="button" disabled={action.isPending} onClick={() => action.mutate({ id: claimId, action: "submit" })} className="rounded-md bg-violet-600 px-2 py-1 text-xs font-medium text-white hover:bg-violet-700 disabled:opacity-50">Submit</button>
                </div>
              </div>
              {eob ? (
                <div className="rounded-lg border border-border p-3">
                  <p className="font-semibold text-foreground">Explanation of benefits</p>
                  <p className="text-muted-foreground">{readStr(eob, "summary", "explanation") || "Plain-language EOB available."}</p>
                </div>
              ) : null}
              {events.length ? (
                <div className="rounded-lg border border-border p-3">
                  <p className="mb-1 font-semibold text-foreground">History</p>
                  <ul className="space-y-1">
                    {events.map((e, i) => <li key={i} className="text-xs text-muted-foreground">{readStr(e, "eventType", "type")} · {readStr(e, "createdAt", "created_at")}</li>)}
                  </ul>
                </div>
              ) : null}
            </>
          ) : null}
        </div>
      )}
    </RuvimboSection>
  );
}

function EstimatesPanel() {
  const [cpid, setCpid] = useState("");
  const [charge, setCharge] = useState("");
  const q = useLiabilityEstimates(cpid || undefined);
  const create = useCreateLiabilityEstimate();
  const rows = (q.data ?? []).map(asRecord);
  const latest = useMemo(() => (create.data ? ruvimboObject(create.data) : null), [create.data]);
  return (
    <RuvimboSection title="Patient cost estimates" icon={<Calculator className="h-4 w-4 text-violet-700" />}>
      <div className="grid gap-2 sm:grid-cols-4">
        {field(cpid, setCpid, "Member CPID *")}
        {field(charge, setCharge, "Provider charge *")}
        <button type="button" disabled={!cpid || !charge || create.isPending} onClick={() => create.mutate({ member_cpid: cpid, charge_amount: Number(charge) })} className="rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium text-white hover:bg-violet-700 disabled:opacity-50">
          {create.isPending ? <Loader2 className="mr-1 inline h-4 w-4 animate-spin" /> : null}Estimate
        </button>
      </div>
      <p className="mt-1 text-xs text-muted-foreground">The estimate applies the plan benefit rules to the charge. It is an estimate, not a final claim decision.</p>
      {latest ? (
        <div className="mt-3 rounded-lg border border-border p-3 text-sm">
          <div className="grid grid-cols-2 gap-1 sm:grid-cols-4">
            <div className="text-muted-foreground">Allowed <span className="block font-semibold text-foreground">{readNum(latest, "allowedAmount").toLocaleString()}</span></div>
            <div className="text-muted-foreground">Payer <span className="block font-semibold text-foreground">{readNum(latest, "payerAmount").toLocaleString()}</span></div>
            <div className="text-muted-foreground">Copay <span className="block font-semibold text-foreground">{readNum(latest, "copayAmount", "copay").toLocaleString()}</span></div>
            <div className="text-muted-foreground">Patient <span className="block font-semibold text-foreground">{readNum(latest, "patientResponsibility").toLocaleString()}</span></div>
          </div>
        </div>
      ) : null}
      {cpid && rows.length ? (
        <ul className="mt-3 divide-y divide-border">
          {rows.map((r, i) => (
            <li key={readStr(r, "id") || i} className="flex items-center justify-between gap-2 py-2 text-sm">
              <span className="text-muted-foreground">Charge {readNum(r, "chargeAmount", "charge_amount").toLocaleString()}</span>
              <span className="font-medium text-foreground">Patient {readNum(r, "patientResponsibility").toLocaleString()}</span>
            </li>
          ))}
        </ul>
      ) : null}
    </RuvimboSection>
  );
}
