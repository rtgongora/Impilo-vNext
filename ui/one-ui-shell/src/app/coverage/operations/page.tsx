"use client";

/**
 * Ruvimbo Operations — the admin / payer / provider workbench for the Coverage
 * capability. Exposes every Wave 1-4 backend surface from the browser: payer
 * registry, plan hierarchy + publish, membership verification, authorisations,
 * claims + line-level adjudication + EOB, coordination of benefits, employer
 * rosters, fraud review, capitation, and the claims switch / payer gateway.
 *
 * Route: /coverage/operations (ADMIN-gated in lib/routes.ts). Errors from the
 * engine (409 illegal transition, 400 immutable publish, duplicate 409) surface
 * verbatim — no fake success.
 */

import { useState, type ReactNode } from "react";
import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  usePayers, useCreatePayer, useSuspendPayer, useReactivatePayer,
  useSchemes, useCreateScheme, useProducts, useCreateProduct,
  usePlanVersions, useCreatePlanVersion, usePublishPlanVersion, usePlanBenefits, useCreateBenefit,
  usePendingVerification, useVerifyMembership, useTransitionMembership,
  useAuthorisations, useCreateAuthorisation, useAuthorisationAction, useAuthorisation,
  useCreateClaimV2, useClaimV2, useClaimAction, useClaimEob,
  useCobList, useCoordinateCob,
  useEmployers, useRegisterEmployer, useStageRoster, useRosterBatch, useRosterRows, useRosterAction,
  useFraudFlags, useScreenClaim, useReviewFraudFlag,
  useCapitationReports, useCreateCapitation,
  useGatewayTransactions, useRouteGateway,
} from "@/hooks/queries/useRuvimbo";

// ── small helpers ───────────────────────────────────────────────────────────
type Row = Record<string, unknown>;
const rstr = (r: Row, ...k: string[]) => { for (const x of k) { const v = r[x]; if (typeof v === "string" && v) return v; if (typeof v === "number") return String(v); } return ""; };
const rnum = (r: Row, ...k: string[]) => { for (const x of k) { const v = r[x]; if (typeof v === "number") return v; if (typeof v === "string" && v.trim() && !Number.isNaN(Number(v))) return Number(v); } return 0; };

function errText(e: unknown): string {
  const any = e as { message?: string; response?: { data?: unknown }; body?: unknown };
  const body = any?.response?.data ?? any?.body;
  if (body && typeof body === "object") {
    const err = (body as { error?: { message?: string } }).error;
    if (err?.message) return err.message;
  }
  return any?.message ?? "Request failed";
}

function Err({ error }: { error: unknown }) {
  if (!error) return null;
  return <p className="mt-2 rounded bg-rose-50 px-2 py-1 text-xs text-rose-700" data-testid="ruvimbo-error">{errText(error)}</p>;
}

function Section({ title, children, action }: { title: string; children: ReactNode; action?: ReactNode }) {
  return (
    <div className="rounded-lg border border-gray-200 bg-white p-4">
      <div className="mb-3 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-foreground">{title}</h3>
        {action}
      </div>
      {children}
    </div>
  );
}

function Field({ label, value, onChange, placeholder, type = "text" }: {
  label: string; value: string; onChange: (v: string) => void; placeholder?: string; type?: string;
}) {
  return (
    <label className="block text-xs">
      <span className="mb-1 block text-muted-foreground">{label}</span>
      <input className="w-full rounded border border-gray-300 px-2 py-1 text-sm" type={type} value={value}
        placeholder={placeholder} onChange={(e) => onChange(e.target.value)} />
    </label>
  );
}

function Table({ cols, rows, render, empty = "No records." }: {
  cols: string[]; rows: Row[]; render: (r: Row, i: number) => ReactNode[]; empty?: string;
}) {
  if (!rows.length) return <p className="text-sm text-muted-foreground">{empty}</p>;
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead><tr className="border-b text-left text-muted-foreground">{cols.map((c) => <th key={c} className="pb-2 pr-3 font-medium">{c}</th>)}</tr></thead>
        <tbody className="divide-y divide-gray-100">
          {rows.map((r, i) => <tr key={rstr(r, "id") || i}>{render(r, i).map((cell, j) => <td key={j} className="py-2 pr-3 align-top">{cell}</td>)}</tr>)}
        </tbody>
      </table>
    </div>
  );
}

const Btn = ({ onClick, disabled, children, tone = "primary", testid }: {
  onClick: () => void; disabled?: boolean; children: ReactNode; tone?: "primary" | "ghost" | "danger"; testid?: string;
}) => (
  <button data-testid={testid} onClick={onClick} disabled={disabled}
    className={`rounded px-3 py-1.5 text-xs font-medium disabled:opacity-50 ${
      tone === "primary" ? "bg-teal-600 text-white hover:bg-teal-700"
        : tone === "danger" ? "bg-rose-600 text-white hover:bg-rose-700"
          : "border border-gray-300 text-foreground hover:bg-gray-50"}`}>{children}</button>
);

const Pill = ({ children }: { children: ReactNode }) => (
  <span className="inline-block rounded-full bg-neutral-100 px-2 py-0.5 text-[11px] font-medium text-foreground">{children}</span>
);

// ── Tabs ─────────────────────────────────────────────────────────────────────
type Tab = "payers" | "plans" | "verification" | "authorisations" | "claims" | "cob" | "employers" | "fraud" | "capitation" | "switch";
const TABS: { key: Tab; label: string }[] = [
  { key: "payers", label: "Payers" }, { key: "plans", label: "Plans" }, { key: "verification", label: "Verification" },
  { key: "authorisations", label: "Authorisations" }, { key: "claims", label: "Claims" }, { key: "cob", label: "Coordination" },
  { key: "employers", label: "Employers" }, { key: "fraud", label: "Fraud" }, { key: "capitation", label: "Capitation" },
  { key: "switch", label: "Switch" },
];

export default function RuvimboOperationsPage() {
  const [tab, setTab] = useState<Tab>("payers");
  return (
    <AppLayout>
      <PageShell title="Ruvimbo Operations" subtitle="Health financing — payers, plans, authorisations, claims & settlement" serviceSlug="ruvimbo">
        <div className="mb-4 flex flex-wrap items-center gap-4 rounded-lg border border-teal-200 bg-white px-4 py-3">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/brand/services/ruvimbo-logo.png" alt="Ruvimbo" className="h-9 w-auto" />
          <span className="text-xs text-teal-800">
            The Coverage capability — Coverage decides who pays &amp; what is covered; COSTA prices, MUSHEX moves money.
          </span>
          <Link href="/coverage" className="ml-auto text-xs text-teal-700 underline">Legacy coverage console →</Link>
        </div>
        <div className="mb-4 flex flex-wrap gap-1 border-b">
          {TABS.map((t) => (
            <button key={t.key} onClick={() => setTab(t.key)} data-testid={`ruvimbo-tab-${t.key}`}
              className={`border-b-2 px-3 py-2 text-sm font-medium ${tab === t.key ? "border-teal-600 text-teal-700" : "border-transparent text-muted-foreground hover:text-foreground"}`}>
              {t.label}
            </button>
          ))}
        </div>
        {tab === "payers" && <PayersTab />}
        {tab === "plans" && <PlansTab />}
        {tab === "verification" && <VerificationTab />}
        {tab === "authorisations" && <AuthorisationsTab />}
        {tab === "claims" && <ClaimsTab />}
        {tab === "cob" && <CobTab />}
        {tab === "employers" && <EmployersTab />}
        {tab === "fraud" && <FraudTab />}
        {tab === "capitation" && <CapitationTab />}
        {tab === "switch" && <SwitchTab />}
      </PageShell>
    </AppLayout>
  );
}

// ── Payers ────────────────────────────────────────────────────────────────
function PayersTab() {
  const payers = usePayers();
  const create = useCreatePayer();
  const suspend = useSuspendPayer();
  const reactivate = useReactivatePayer();
  const [code, setCode] = useState(""); const [name, setName] = useState(""); const [type, setType] = useState("MEDICAL_AID");
  const rows = payers.data ?? [];
  return (
    <div className="grid gap-4 lg:grid-cols-3">
      <Section title="Register payer">
        <div className="space-y-2">
          <Field label="Payer code" value={code} onChange={setCode} placeholder="PAY-EXAMPLE" />
          <Field label="Legal name" value={name} onChange={setName} placeholder="Example Medical Aid" />
          <Field label="Organisation type" value={type} onChange={setType} placeholder="MEDICAL_AID" />
          <Btn testid="ruvimbo-create-payer" disabled={!code || !name || create.isPending}
            onClick={() => create.mutate({ payerCode: code, legalName: name, organisationType: type },
              { onSuccess: () => { setCode(""); setName(""); } })}>Register</Btn>
          <Err error={create.error} />
        </div>
      </Section>
      <div className="lg:col-span-2">
        <Section title={`Payer registry (${rows.length})`}>
          <Table cols={["Code", "Name", "Type", "Status", ""]} rows={rows} render={(r) => [
            <span key="code" className="font-medium">{rstr(r, "payerCode")}</span>, rstr(r, "legalName"), <Pill key="type">{rstr(r, "organisationType")}</Pill>,
            <Pill key="status">{rstr(r, "status")}</Pill>,
            rstr(r, "status") === "ACTIVE"
              ? <Btn tone="danger" onClick={() => suspend.mutate(rstr(r, "id"))}>Suspend</Btn>
              : <Btn tone="ghost" onClick={() => reactivate.mutate(rstr(r, "id"))}>Reactivate</Btn>,
          ]} />
          <Err error={suspend.error || reactivate.error} />
        </Section>
      </div>
    </div>
  );
}

// ── Plans (hierarchy + publish) ──────────────────────────────────────────────
function PlansTab() {
  const payers = usePayers();
  const [payerId, setPayerId] = useState("");
  const schemes = useSchemes(payerId || undefined);
  const [schemeId, setSchemeId] = useState("");
  const products = useProducts(schemeId || undefined);
  const [productId, setProductId] = useState("");
  const versions = usePlanVersions(productId || undefined);
  const [versionId, setVersionId] = useState("");
  const benefits = usePlanBenefits(versionId || undefined);

  const createScheme = useCreateScheme(payerId);
  const createProduct = useCreateProduct(schemeId);
  const createVersion = useCreatePlanVersion(productId);
  const publish = usePublishPlanVersion(productId);
  const createBenefit = useCreateBenefit(versionId);

  const [schemeCode, setSchemeCode] = useState(""); const [schemeName, setSchemeName] = useState(""); const [covType, setCovType] = useState("MEDICAL_AID");
  const [prodCode, setProdCode] = useState(""); const [prodName, setProdName] = useState("");
  const [benCode, setBenCode] = useState(""); const [benCat, setBenCat] = useState("PROFESSIONAL_FEE"); const [benPct, setBenPct] = useState("100"); const [benLimit, setBenLimit] = useState("1000");

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <Section title="1 · Payer → Scheme → Product → Version">
        <label className="block text-xs">
          <span className="mb-1 block text-muted-foreground">Payer</span>
          <select className="w-full rounded border border-gray-300 px-2 py-1 text-sm" value={payerId}
            onChange={(e) => { setPayerId(e.target.value); setSchemeId(""); setProductId(""); setVersionId(""); }}>
            <option value="">Select payer</option>
            {(payers.data ?? []).map((p) => <option key={rstr(p, "id")} value={rstr(p, "id")}>{rstr(p, "legalName")} ({rstr(p, "payerCode")})</option>)}
          </select>
        </label>
        {payerId && (
          <div className="mt-3 space-y-2 border-t pt-3">
            <p className="text-xs font-medium text-muted-foreground">Schemes</p>
            <Table cols={["Code", "Type", ""]} rows={schemes.data ?? []} empty="No schemes." render={(r) => [
              rstr(r, "schemeCode"), <Pill key="type">{rstr(r, "coverageType")}</Pill>,
              <Btn key="open" tone="ghost" onClick={() => { setSchemeId(rstr(r, "id")); setProductId(""); setVersionId(""); }}>Open</Btn>,
            ]} />
            <div className="grid grid-cols-2 gap-2">
              <Field label="Scheme code" value={schemeCode} onChange={setSchemeCode} />
              <Field label="Coverage type" value={covType} onChange={setCovType} />
            </div>
            <Field label="Scheme name" value={schemeName} onChange={setSchemeName} />
            <Btn disabled={!schemeCode || !schemeName || createScheme.isPending}
              onClick={() => createScheme.mutate({ schemeCode, name: schemeName, coverageType: covType }, { onSuccess: () => { setSchemeCode(""); setSchemeName(""); } })}>Add scheme</Btn>
            <Err error={createScheme.error} />
          </div>
        )}
        {schemeId && (
          <div className="mt-3 space-y-2 border-t pt-3">
            <p className="text-xs font-medium text-muted-foreground">Products</p>
            <Table cols={["Code", "Name", ""]} rows={products.data ?? []} empty="No products." render={(r) => [
              rstr(r, "productCode"), rstr(r, "name"),
              <Btn key="open" tone="ghost" onClick={() => { setProductId(rstr(r, "id")); setVersionId(""); }}>Open</Btn>,
            ]} />
            <div className="grid grid-cols-2 gap-2">
              <Field label="Product code" value={prodCode} onChange={setProdCode} />
              <Field label="Product name" value={prodName} onChange={setProdName} />
            </div>
            <Btn disabled={!prodCode || !prodName || createProduct.isPending}
              onClick={() => createProduct.mutate({ productCode: prodCode, name: prodName }, { onSuccess: () => { setProdCode(""); setProdName(""); } })}>Add product</Btn>
            <Err error={createProduct.error} />
          </div>
        )}
      </Section>

      <Section title="2 · Plan versions & benefits">
        {!productId ? <p className="text-sm text-muted-foreground">Select a product to manage versions.</p> : (
          <div className="space-y-3">
            <Table cols={["Version", "Status", "Currency", ""]} rows={versions.data ?? []} empty="No versions." render={(r) => [
              `v${rstr(r, "versionNumber")}`, <Pill key="status">{rstr(r, "status")}</Pill>, rstr(r, "currency"),
              <div key="actions" className="flex gap-1">
                <Btn tone="ghost" onClick={() => setVersionId(rstr(r, "id"))}>Benefits</Btn>
                {rstr(r, "status") === "DRAFT" && <Btn testid="ruvimbo-publish" onClick={() => publish.mutate(rstr(r, "id"))}>Publish</Btn>}
              </div>,
            ]} />
            <Btn disabled={createVersion.isPending} onClick={() => createVersion.mutate({ currency: "USD", approvalAuthority: "ops-console" })}>New draft version</Btn>
            <Err error={createVersion.error || publish.error} />
            {versionId && (
              <div className="space-y-2 border-t pt-3">
                <p className="text-xs font-medium text-muted-foreground">Benefits on this version</p>
                <Table cols={["Code", "Category", "%", "Copay", "Auth"]} rows={benefits.data ?? []} empty="No benefits." render={(r) => [
                  rstr(r, "benefitCode"), rstr(r, "category"), `${rnum(r, "coveragePercent")}%`, rnum(r, "copayAmount").toFixed(2),
                  r.requiresAuthorisation ? <Pill>auth</Pill> : "—",
                ]} />
                <div className="grid grid-cols-2 gap-2">
                  <Field label="Benefit code" value={benCode} onChange={setBenCode} placeholder="GP_CONSULT" />
                  <Field label="Category" value={benCat} onChange={setBenCat} />
                  <Field label="Coverage %" value={benPct} onChange={setBenPct} type="number" />
                  <Field label="Annual limit" value={benLimit} onChange={setBenLimit} type="number" />
                </div>
                <Btn disabled={!benCode || createBenefit.isPending}
                  onClick={() => createBenefit.mutate({ benefitCode: benCode, category: benCat, coveragePercent: Number(benPct), annualLimit: Number(benLimit) },
                    { onSuccess: () => setBenCode("") })}>Add benefit</Btn>
                <Err error={createBenefit.error} />
              </div>
            )}
          </div>
        )}
      </Section>
    </div>
  );
}

// ── Verification queue ───────────────────────────────────────────────────────
function VerificationTab() {
  const pending = usePendingVerification();
  const verify = useVerifyMembership();
  const transition = useTransitionMembership();
  const rows = pending.data ?? [];
  return (
    <Section title={`Membership verification queue (${rows.length})`}>
      <Table cols={["Client", "Status", "Verification", "Actions"]} rows={rows}
        empty="No memberships awaiting verification." render={(r) => {
          const id = rstr(r, "id");
          return [
            rstr(r, "clientId"), <Pill key="status">{rstr(r, "status")}</Pill>, <Pill key="verification">{rstr(r, "verificationStatus")}</Pill>,
            <div key="actions" className="flex gap-1">
              <Btn testid="ruvimbo-verify" onClick={() => verify.mutate({ id, body: { verified: true, source: "REGISTRY_ATTESTED" } })}>Verify</Btn>
              <Btn tone="ghost" onClick={() => verify.mutate({ id, body: { verified: false } })}>Dispute</Btn>
              <Btn tone="danger" onClick={() => transition.mutate({ id, action: "terminate", body: { reason: "ops" } })}>Terminate</Btn>
            </div>,
          ];
        }} />
      <Err error={verify.error || transition.error} />
    </Section>
  );
}

// ── Authorisations workbench ─────────────────────────────────────────────────
function AuthorisationsTab() {
  const [cpid, setCpid] = useState("");
  const [status, setStatus] = useState("SUBMITTED");
  const auths = useAuthorisations({ memberCpid: cpid || undefined, status: cpid ? undefined : status });
  const [openId, setOpenId] = useState("");
  const detail = useAuthorisation(openId || undefined);
  const action = useAuthorisationAction();
  const rows = auths.data ?? [];
  const lines = (detail.data?.lines as Row[] | undefined) ?? [];
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <Section title="Find authorisations">
        <div className="grid grid-cols-2 gap-2">
          <Field label="Member CPID" value={cpid} onChange={setCpid} placeholder="cpid…" />
          <Field label="Or queue status" value={status} onChange={setStatus} placeholder="SUBMITTED" />
        </div>
        <div className="mt-3">
          <Table cols={["Type", "Status", "Urgency", ""]} rows={rows} empty="No authorisations." render={(r) => [
            rstr(r, "authType"), <Pill key="status">{rstr(r, "status")}</Pill>, rstr(r, "urgency"),
            <Btn key="review" tone="ghost" onClick={() => setOpenId(rstr(r, "id"))}>Review</Btn>,
          ]} />
        </div>
      </Section>
      <Section title="Reviewer actions">
        {!openId ? <p className="text-sm text-muted-foreground">Select an authorisation to review.</p> : (
          <div className="space-y-3">
            <p className="text-xs text-muted-foreground">Status: <Pill>{rstr(detail.data ?? {}, "status")}</Pill></p>
            <Table cols={["Benefit", "Qty", "Est", "Line"]} rows={lines} render={(l) => [
              rstr(l, "benefitCode"), rnum(l, "quantityRequested"), rnum(l, "estimatedAmount").toFixed(2), <Pill key="line">{rstr(l, "lineStatus")}</Pill>,
            ]} />
            <div className="flex flex-wrap gap-1">
              <Btn tone="ghost" onClick={() => action.mutate({ id: openId, action: "submit" })}>Submit</Btn>
              <Btn tone="ghost" onClick={() => action.mutate({ id: openId, action: "acknowledge", body: { reviewerId: "ops" } })}>Acknowledge</Btn>
              <Btn testid="ruvimbo-approve-auth" onClick={() => action.mutate({ id: openId, action: "decision",
                body: { reviewerId: "ops", lines: lines.map((l) => ({ lineId: rstr(l, "id"), outcome: "APPROVED", quantityApproved: rnum(l, "quantityRequested"), approvedAmount: rnum(l, "estimatedAmount") })) } })}>Approve all</Btn>
              <Btn tone="danger" onClick={() => action.mutate({ id: openId, action: "decision",
                body: { reviewerId: "ops", lines: lines.map((l) => ({ lineId: rstr(l, "id"), outcome: "DENIED", reason: "not indicated" })) } })}>Deny all</Btn>
            </div>
            <Err error={action.error} />
          </div>
        )}
      </Section>
    </div>
  );
}

// ── Claims + adjudication ────────────────────────────────────────────────────
function ClaimsTab() {
  const [coverageId, setCoverageId] = useState("");
  const [benefit, setBenefit] = useState("GP_CONSULT");
  const [amount, setAmount] = useState("30");
  const [authId, setAuthId] = useState("");
  const create = useCreateClaimV2();
  const [claimId, setClaimId] = useState("");
  const claim = useClaimV2(claimId || undefined);
  const eob = useClaimEob(claimId || undefined);
  const action = useClaimAction();
  const c = claim.data ?? {};
  const lines = (c.lines as Row[] | undefined) ?? [];
  const eobLines = (eob.data?.lines as Row[] | undefined) ?? [];
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <Section title="File a claim (v2)">
        <div className="space-y-2">
          <Field label="Coverage / membership ID" value={coverageId} onChange={setCoverageId} placeholder="membership uuid" />
          <div className="grid grid-cols-2 gap-2">
            <Field label="Benefit code" value={benefit} onChange={setBenefit} />
            <Field label="Amount" value={amount} onChange={setAmount} type="number" />
          </div>
          <Field label="Authorisation ID (optional)" value={authId} onChange={setAuthId} />
          <Btn testid="ruvimbo-file-claim" disabled={!coverageId || create.isPending}
            onClick={() => create.mutate({ coverageId, claimType: "OUTPATIENT", facilityId: "FAC-1", providerId: "PRV-1",
              authorisationId: authId || undefined, lines: [{ benefitCode: benefit, submittedAmount: Number(amount) }] },
              { onSuccess: (res) => setClaimId(rstr((res as { data?: Row }).data ?? {}, "id")) })}>Create draft</Btn>
          <Err error={create.error} />
        </div>
      </Section>
      <Section title="Adjudicate & explain">
        {!claimId ? <p className="text-sm text-muted-foreground">Create or paste a claim to work it.</p> : (
          <div className="space-y-3">
            <p className="text-xs text-muted-foreground">{rstr(c, "claimNumber")} · <Pill>{rstr(c, "status")}</Pill> · approved {rnum(c, "approvedAmount").toFixed(2)} · patient {rnum(c, "patientResponsibility").toFixed(2)}</p>
            <Table cols={["Benefit", "Submitted", "Approved", "Line"]} rows={lines} render={(l) => [
              rstr(l, "benefitCode"), rnum(l, "submittedAmount").toFixed(2), rnum(l, "approvedAmount").toFixed(2), <Pill key="line">{rstr(l, "lineStatus")}</Pill>,
            ]} />
            <div className="flex flex-wrap gap-1">
              <Btn tone="ghost" onClick={() => action.mutate({ id: claimId, action: "scrub" })}>Scrub</Btn>
              <Btn tone="ghost" onClick={() => action.mutate({ id: claimId, action: "submit" })}>Submit</Btn>
              <Btn testid="ruvimbo-adjudicate" onClick={() => action.mutate({ id: claimId, action: "adjudicate" })}>Adjudicate</Btn>
              <Btn tone="danger" onClick={() => action.mutate({ id: claimId, action: "void", body: { reason: "ops" } })}>Void</Btn>
            </div>
            <Err error={action.error} />
            {eobLines.length > 0 && (
              <div className="border-t pt-2">
                <p className="mb-1 text-xs font-medium text-muted-foreground">Explanation of Benefits</p>
                <Table cols={["Service", "Charged", "Paid", "You owe", "Why"]} rows={eobLines} render={(l) => [
                  rstr(l, "service"), rnum(l, "charged").toFixed(2), rnum(l, "paidByPayer").toFixed(2), rnum(l, "yourResponsibility").toFixed(2),
                  <span key="why" className="text-xs text-muted-foreground">{rstr(l, "explanation")}</span>,
                ]} />
              </div>
            )}
          </div>
        )}
      </Section>
    </div>
  );
}

// ── Coordination of benefits ─────────────────────────────────────────────────
function CobTab() {
  const [cpid, setCpid] = useState("");
  const [benefit, setBenefit] = useState("GP_CONSULT");
  const [charge, setCharge] = useState("100");
  const list = useCobList(cpid || undefined);
  const coordinate = useCoordinateCob();
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <Section title="Coordinate benefits">
        <div className="space-y-2">
          <Field label="Member CPID" value={cpid} onChange={setCpid} />
          <div className="grid grid-cols-2 gap-2">
            <Field label="Benefit" value={benefit} onChange={setBenefit} />
            <Field label="Standard charge" value={charge} onChange={setCharge} type="number" />
          </div>
          <Btn testid="ruvimbo-cob" disabled={!cpid || coordinate.isPending}
            onClick={() => coordinate.mutate({ memberCpid: cpid, benefitCode: benefit, standardCharge: Number(charge) })}>Run waterfall</Btn>
          <Err error={coordinate.error} />
        </div>
      </Section>
      <Section title="Recent coordination decisions">
        <Table cols={["Benefit", "Charge", "Payers paid", "Patient"]} rows={list.data ?? []}
          empty="Enter a CPID to view decisions." render={(r) => [
            rstr(r, "benefitCode"), rnum(r, "standardCharge").toFixed(2),
            <span key="paid" className="font-medium text-teal-700">{rnum(r, "totalPayerPaid").toFixed(2)}</span>, rnum(r, "patientResponsibility").toFixed(2),
          ]} />
      </Section>
    </div>
  );
}

// ── Employer rosters ─────────────────────────────────────────────────────────
function EmployersTab() {
  const employers = useEmployers();
  const register = useRegisterEmployer();
  const stage = useStageRoster();
  const [empCode, setEmpCode] = useState(""); const [empName, setEmpName] = useState("");
  const [batchId, setBatchId] = useState("");
  const batch = useRosterBatch(batchId || undefined);
  const rowsQ = useRosterRows(batchId || undefined);
  const rosterAction = useRosterAction();
  const [employerId, setEmployerId] = useState("");
  const [planId, setPlanId] = useState("");
  const [clients, setClients] = useState("");
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <Section title="Employers & staging">
        <div className="grid grid-cols-2 gap-2">
          <Field label="Employer code" value={empCode} onChange={setEmpCode} />
          <Field label="Name" value={empName} onChange={setEmpName} />
        </div>
        <Btn disabled={!empCode || !empName || register.isPending}
          onClick={() => register.mutate({ employerCode: empCode, name: empName }, { onSuccess: () => { setEmpCode(""); setEmpName(""); } })}>Register employer</Btn>
        <Err error={register.error} />
        <div className="mt-3 border-t pt-3">
          <Table cols={["Code", "Name", ""]} rows={employers.data ?? []} empty="No employers." render={(r) => [
            rstr(r, "employerCode"), rstr(r, "name"),
            <Btn key="select" tone="ghost" onClick={() => setEmployerId(rstr(r, "id"))}>Select</Btn>,
          ]} />
        </div>
        {employerId && (
          <div className="mt-3 space-y-2 border-t pt-3">
            <p className="text-xs font-medium text-muted-foreground">Stage roster for employer</p>
            <Field label="Plan ID (flat cv_coverage_plans)" value={planId} onChange={setPlanId} placeholder="bbbbbbbb-0001-0000-0000-000000000002" />
            <Field label="Client IDs (comma-separated)" value={clients} onChange={setClients} placeholder="emp-1, emp-2" />
            <Btn testid="ruvimbo-stage-roster" disabled={!planId || !clients || stage.isPending}
              onClick={() => stage.mutate({ employerId, body: { planId, uploadedBy: "hr",
                rows: clients.split(",").map((c) => ({ clientId: c.trim(), relationship: "SELF" })).filter((r) => r.clientId) } },
                { onSuccess: (res) => setBatchId(rstr((res as { data?: Row }).data ?? {}, "id")) })}>Stage batch</Btn>
            <Err error={stage.error} />
          </div>
        )}
      </Section>
      <Section title="Roster batch → validate → apply">
        {!batchId ? <p className="text-sm text-muted-foreground">Stage a roster to validate & apply.</p> : (
          <div className="space-y-3">
            <p className="text-xs text-muted-foreground">
              <Pill>{rstr(batch.data ?? {}, "status")}</Pill> total {rnum(batch.data ?? {}, "totalRows")} · valid {rnum(batch.data ?? {}, "validRows")} · applied {rnum(batch.data ?? {}, "appliedRows")}
            </p>
            <Table cols={["Client", "Rel", "Validation", "Error"]} rows={rowsQ.data ?? []} render={(r) => [
              rstr(r, "clientId"), rstr(r, "relationship"), <Pill key="validation">{rstr(r, "validationStatus")}</Pill>, rstr(r, "validationError") || "—",
            ]} />
            <div className="flex gap-1">
              <Btn onClick={() => rosterAction.mutate({ batchId, action: "validate" })}>Validate</Btn>
              <Btn testid="ruvimbo-apply-roster" onClick={() => rosterAction.mutate({ batchId, action: "apply" })}>Apply</Btn>
            </div>
            <Err error={rosterAction.error} />
          </div>
        )}
      </Section>
    </div>
  );
}

// ── Fraud ────────────────────────────────────────────────────────────────────
function FraudTab() {
  const flags = useFraudFlags();
  const screen = useScreenClaim();
  const review = useReviewFraudFlag();
  const [claimId, setClaimId] = useState("");
  const rows = flags.data ?? [];
  return (
    <div className="grid gap-4 lg:grid-cols-3">
      <Section title="Screen a claim">
        <Field label="Claim ID" value={claimId} onChange={setClaimId} />
        <Btn testid="ruvimbo-screen" disabled={!claimId || screen.isPending} onClick={() => screen.mutate(claimId)}>Screen for fraud</Btn>
        <Err error={screen.error} />
      </Section>
      <div className="lg:col-span-2">
        <Section title={`Fraud flags (${rows.length})`}>
          <Table cols={["Type", "Subject", "Severity", "Status", ""]} rows={rows} empty="No flags." render={(r) => {
            const id = rstr(r, "id");
            return [
              <span key="type" className="font-medium">{rstr(r, "flagType")}</span>, rstr(r, "subjectRef").slice(0, 12), <Pill key="severity">{rstr(r, "severity")}</Pill>, <Pill key="status">{rstr(r, "status")}</Pill>,
              rstr(r, "status") === "OPEN" ? (
                <div className="flex gap-1">
                  <Btn onClick={() => review.mutate({ id, body: { status: "CONFIRMED", reviewerId: "inv", resolution: "recover" } })}>Confirm</Btn>
                  <Btn tone="ghost" onClick={() => review.mutate({ id, body: { status: "DISMISSED", reviewerId: "inv" } })}>Dismiss</Btn>
                </div>
              ) : "—",
            ];
          }} />
          <Err error={review.error} />
        </Section>
      </div>
    </div>
  );
}

// ── Capitation ───────────────────────────────────────────────────────────────
function CapitationTab() {
  const [provider, setProvider] = useState("");
  const list = useCapitationReports(provider || undefined);
  const create = useCreateCapitation();
  const [members, setMembers] = useState("250"); const [encounters, setEncounters] = useState("400"); const [amount, setAmount] = useState("5000");
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <Section title="Submit capitation report">
        <div className="space-y-2">
          <Field label="Provider ID" value={provider} onChange={setProvider} />
          <div className="grid grid-cols-3 gap-2">
            <Field label="Members" value={members} onChange={setMembers} type="number" />
            <Field label="Encounters" value={encounters} onChange={setEncounters} type="number" />
            <Field label="Amount" value={amount} onChange={setAmount} type="number" />
          </div>
          <Btn disabled={!provider || create.isPending}
            onClick={() => create.mutate({ providerId: provider, facilityId: "FAC-1", payerId: "PAYER-MOHCC",
              periodStart: "2026-07-01", periodEnd: "2026-07-31", memberCount: Number(members), encounterCount: Number(encounters), capitationAmount: Number(amount) })}>Submit</Btn>
          <Err error={create.error} />
        </div>
      </Section>
      <Section title="Reports">
        <Table cols={["Period", "Members", "Encounters", "Amount"]} rows={list.data ?? []}
          empty="Enter a provider ID." render={(r) => [
            `${rstr(r, "periodStart")}→${rstr(r, "periodEnd")}`, rnum(r, "memberCount"), rnum(r, "encounterCount"), rnum(r, "capitationAmount").toFixed(2),
          ]} />
      </Section>
    </div>
  );
}

// ── Claims switch / gateway ──────────────────────────────────────────────────
function SwitchTab() {
  const list = useGatewayTransactions("ROUTED");
  const route = useRouteGateway();
  const [type, setType] = useState("CLAIM_SUBMISSION");
  const [payer, setPayer] = useState("PAYER-MOHCC");
  const [routeKind, setRouteKind] = useState("INTERNAL_ADJUDICATION");
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <Section title="Route a transaction">
        <div className="space-y-2">
          <Field label="Transaction type" value={type} onChange={setType} />
          <Field label="Receiver payer" value={payer} onChange={setPayer} />
          <label className="block text-xs">
            <span className="mb-1 block text-muted-foreground">Route</span>
            <select className="w-full rounded border border-gray-300 px-2 py-1 text-sm" value={routeKind} onChange={(e) => setRouteKind(e.target.value)}>
              <option value="INTERNAL_ADJUDICATION">INTERNAL_ADJUDICATION</option>
              <option value="EXTERNAL_API">EXTERNAL_API (gated)</option>
              <option value="EXTERNAL_MANUAL">EXTERNAL_MANUAL (gated)</option>
            </select>
          </label>
          <Btn testid="ruvimbo-route" disabled={route.isPending}
            onClick={() => route.mutate({ transactionType: type, senderRef: "FAC-1", receiverPayer: payer, route: routeKind })}>Route</Btn>
          <Err error={route.error} />
          <p className="text-xs text-muted-foreground">External rails are credential-gated — business ack stays PENDING (never faked as accepted).</p>
        </div>
      </Section>
      <Section title="Switch monitor (routed)">
        <Table cols={["SCN", "Type", "Route", "Tech", "Business"]} rows={list.data ?? []}
          empty="No transactions." render={(r) => [
            <span key="scn" className="font-mono text-[11px]">{rstr(r, "switchControlNumber")}</span>, rstr(r, "transactionType"), <Pill key="route">{rstr(r, "route")}</Pill>,
            <Pill key="tech">{rstr(r, "technicalAck")}</Pill>, <Pill key="business">{rstr(r, "businessAck")}</Pill>,
          ]} />
      </Section>
    </div>
  );
}
