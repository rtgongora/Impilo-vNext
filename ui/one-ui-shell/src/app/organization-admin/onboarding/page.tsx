"use client";

/**
 * Organization onboarding wizard — DRAFT → representatives → submit-verification →
 * national-admin verify. Step-machine idiom (useState) over the useOrgOnboarding
 * hooks (Experience → BFF → organization-registry).
 *
 * Route: /organization-admin/onboarding | role-gated ORGANIZATION_ADMIN.
 */

import Link from "next/link";
import { useState } from "react";
import { ArrowLeft, Building2, CheckCircle2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import {
  useAddRepresentative,
  useCreateOrganization,
  useRepresentatives,
  useSubmitVerification,
  useVerifyOrganization,
} from "@/hooks/queries/useOrgOnboarding";

type Step = "create" | "representatives" | "submit" | "verify" | "done";

const STEP_LABELS: Record<Step, string> = {
  create: "Create organization",
  representatives: "Authorized representatives",
  submit: "Submit for verification",
  verify: "National-admin verification",
  done: "Verified",
};

function errText(e: unknown): string {
  if (e && typeof e === "object" && "error" in e) {
    return String((e as { error?: { message?: string } }).error?.message ?? "Request failed");
  }
  return e instanceof Error ? e.message : "Request failed";
}

export default function OrgOnboardingWizardPage() {
  const [step, setStep] = useState<Step>("create");
  const [orgId, setOrgId] = useState<string>("");

  const create = useCreateOrganization();
  const addRep = useAddRepresentative();
  const submit = useSubmitVerification();
  const verify = useVerifyOrganization();
  const reps = useRepresentatives(orgId || undefined);

  const [orgForm, setOrgForm] = useState({
    code: "",
    legalName: "",
    orgType: "PROVIDER",
    countryOperationRef: "",
    registrationNumber: "",
  });
  const [repForm, setRepForm] = useState({ personHealthId: "", roleTitle: "", evidenceRef: "" });
  const [verifiedIds, setVerifiedIds] = useState<string[]>([]);

  const repRows = reps.data?.data ?? [];
  const steps: Step[] = ["create", "representatives", "submit", "verify"];

  function submitCreate() {
    create.mutate(
      {
        code: orgForm.code.trim(),
        legalName: orgForm.legalName.trim(),
        orgType: orgForm.orgType.trim(),
        countryOperationRef: orgForm.countryOperationRef.trim() || undefined,
        registrationIdentifiers: orgForm.registrationNumber.trim()
          ? { registrationNumber: orgForm.registrationNumber.trim() }
          : undefined,
      },
      {
        onSuccess: (res) => {
          setOrgId(res.data?.id ?? "");
          setStep("representatives");
        },
      },
    );
  }

  function submitAddRep() {
    if (!orgId) return;
    addRep.mutate(
      {
        organizationId: orgId,
        personHealthId: repForm.personHealthId.trim(),
        roleTitle: repForm.roleTitle.trim(),
        evidenceRef: repForm.evidenceRef.trim() || undefined,
      },
      { onSuccess: () => setRepForm({ personHealthId: "", roleTitle: "", evidenceRef: "" }) },
    );
  }

  function submitForVerification() {
    if (!orgId) return;
    submit.mutate(orgId, { onSuccess: () => setStep("verify") });
  }

  function submitVerify() {
    if (!orgId) return;
    verify.mutate(
      { organizationId: orgId, verifiedRepresentativeIds: verifiedIds },
      { onSuccess: () => setStep("done") },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Organization onboarding"
        subtitle="Register a legal entity, attach authorized representatives, submit for verification, and record the national-admin verification decision."
      >
        <OrganizationPlaneContextBar />

        <div className="my-6">
          <Link
            href="/organization-admin"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Organisation admin
          </Link>
        </div>

        {/* Step indicator */}
        <ol className="mb-6 flex flex-wrap gap-2">
          {steps.map((s, i) => {
            const active = step === s;
            const complete = steps.indexOf(step) > i || step === "done";
            return (
              <li
                key={s}
                className={`flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-medium ${
                  active
                    ? "border-primary bg-indigo-50 text-primary-hover"
                    : complete
                      ? "border-emerald-300 bg-emerald-50 text-emerald-800"
                      : "border-border bg-card text-muted-foreground"
                }`}
              >
                {complete ? <CheckCircle2 className="h-4 w-4" /> : <span>{i + 1}</span>}
                {STEP_LABELS[s]}
              </li>
            );
          })}
        </ol>

        {step === "create" && (
          <section className="rounded-lg border border-border bg-card p-5 shadow-sm">
            <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
              <Building2 className="h-4 w-4 text-primary-hover" /> Organization details (DRAFT)
            </div>
            <div className="mt-4 grid gap-3 sm:grid-cols-2">
              <input aria-label="Organization code" placeholder="Organization code" value={orgForm.code}
                onChange={(e) => setOrgForm((f) => ({ ...f, code: e.target.value }))}
                className="rounded-md border border-border bg-background px-3 py-2 text-sm" />
              <input aria-label="Legal name" placeholder="Legal name" value={orgForm.legalName}
                onChange={(e) => setOrgForm((f) => ({ ...f, legalName: e.target.value }))}
                className="rounded-md border border-border bg-background px-3 py-2 text-sm" />
              <input aria-label="Organization type" placeholder="Organization type" value={orgForm.orgType}
                onChange={(e) => setOrgForm((f) => ({ ...f, orgType: e.target.value }))}
                className="rounded-md border border-border bg-background px-3 py-2 text-sm" />
              <input aria-label="Country operation ref" placeholder="Country operation ref (optional)" value={orgForm.countryOperationRef}
                onChange={(e) => setOrgForm((f) => ({ ...f, countryOperationRef: e.target.value }))}
                className="rounded-md border border-border bg-background px-3 py-2 text-sm" />
              <input aria-label="Registration number" placeholder="Registration number (optional)" value={orgForm.registrationNumber}
                onChange={(e) => setOrgForm((f) => ({ ...f, registrationNumber: e.target.value }))}
                className="rounded-md border border-border bg-background px-3 py-2 text-sm" />
            </div>
            {create.isError && <p className="mt-2 text-xs text-red-600">{errText(create.error)}</p>}
            <button type="button"
              disabled={!orgForm.code.trim() || !orgForm.legalName.trim() || create.isPending}
              onClick={submitCreate}
              className="mt-4 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-indigo-800 disabled:cursor-not-allowed disabled:opacity-50">
              {create.isPending ? "Creating…" : "Create organization"}
            </button>
          </section>
        )}

        {step === "representatives" && (
          <section className="space-y-4">
            <div className="rounded-lg border border-border bg-card p-5 shadow-sm">
              <div className="text-sm font-semibold text-foreground">Add authorized representative</div>
              <p className="mt-1 text-xs text-muted-foreground">Organization <span className="font-mono">{orgId}</span></p>
              <div className="mt-4 grid gap-3 sm:grid-cols-3">
                <input aria-label="Person health id" placeholder="Person health id" value={repForm.personHealthId}
                  onChange={(e) => setRepForm((f) => ({ ...f, personHealthId: e.target.value }))}
                  className="rounded-md border border-border bg-background px-3 py-2 text-sm" />
                <input aria-label="Role title" placeholder="Role title" value={repForm.roleTitle}
                  onChange={(e) => setRepForm((f) => ({ ...f, roleTitle: e.target.value }))}
                  className="rounded-md border border-border bg-background px-3 py-2 text-sm" />
                <input aria-label="Evidence ref" placeholder="Evidence ref (optional)" value={repForm.evidenceRef}
                  onChange={(e) => setRepForm((f) => ({ ...f, evidenceRef: e.target.value }))}
                  className="rounded-md border border-border bg-background px-3 py-2 text-sm" />
              </div>
              {addRep.isError && <p className="mt-2 text-xs text-red-600">{errText(addRep.error)}</p>}
              <button type="button"
                disabled={!repForm.personHealthId.trim() || !repForm.roleTitle.trim() || addRep.isPending}
                onClick={submitAddRep}
                className="mt-4 rounded-lg border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-background disabled:opacity-50">
                {addRep.isPending ? "Adding…" : "Add representative"}
              </button>
            </div>

            <div className="overflow-hidden rounded-lg border border-border bg-card shadow-sm">
              <div className="border-b border-border bg-background px-4 py-3 text-sm font-medium text-foreground">Representatives</div>
              {repRows.length === 0 ? (
                <div className="px-4 py-6 text-sm text-muted-foreground">None added yet.</div>
              ) : (
                <ul className="divide-y divide-slate-100">
                  {repRows.map((r) => (
                    <li key={r.id} className="flex items-center justify-between gap-3 px-4 py-3 text-sm">
                      <span className="font-medium text-foreground">{r.personHealthId ?? "—"}</span>
                      <span className="text-xs text-muted-foreground">{r.roleTitle ?? "—"} · {r.status ?? "—"}</span>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            <button type="button" disabled={repRows.length === 0}
              onClick={() => setStep("submit")}
              className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-indigo-800 disabled:cursor-not-allowed disabled:opacity-50">
              Continue to verification
            </button>
          </section>
        )}

        {step === "submit" && (
          <section className="rounded-lg border border-border bg-card p-5 shadow-sm">
            <div className="text-sm font-semibold text-foreground">Submit for verification</div>
            <p className="mt-1 text-xs text-muted-foreground">
              Moves organization <span className="font-mono">{orgId}</span> into the national-admin verification queue.
            </p>
            {submit.isError && <p className="mt-2 text-xs text-red-600">{errText(submit.error)}</p>}
            <button type="button" disabled={submit.isPending} onClick={submitForVerification}
              className="mt-4 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-indigo-800 disabled:opacity-50">
              {submit.isPending ? "Submitting…" : "Submit for verification"}
            </button>
          </section>
        )}

        {step === "verify" && (
          <section className="rounded-lg border border-border bg-card p-5 shadow-sm">
            <div className="text-sm font-semibold text-foreground">National-admin verification</div>
            <p className="mt-1 text-xs text-muted-foreground">Select the representatives whose evidence is verified.</p>
            <ul className="mt-4 space-y-2">
              {repRows.length === 0 ? (
                <li className="text-sm text-muted-foreground">No representatives to verify.</li>
              ) : (
                repRows.map((r) => (
                  <li key={r.id} className="flex items-center gap-2 text-sm">
                    <input type="checkbox" id={`vr-${r.id}`}
                      checked={r.id ? verifiedIds.includes(r.id) : false}
                      onChange={(e) =>
                        setVerifiedIds((ids) =>
                          e.target.checked ? [...ids, r.id as string] : ids.filter((x) => x !== r.id),
                        )
                      } />
                    <label htmlFor={`vr-${r.id}`} className="text-foreground">
                      {r.personHealthId ?? "—"} · {r.roleTitle ?? "—"}
                    </label>
                  </li>
                ))
              )}
            </ul>
            {verify.isError && <p className="mt-2 text-xs text-red-600">{errText(verify.error)}</p>}
            <button type="button" disabled={verify.isPending} onClick={submitVerify}
              className="mt-4 rounded-lg bg-emerald-700 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-800 disabled:opacity-50">
              {verify.isPending ? "Verifying…" : "Verify organization"}
            </button>
          </section>
        )}

        {step === "done" && (
          <section className="rounded-lg border border-emerald-300 bg-emerald-50 p-5 text-sm text-emerald-800">
            <div className="flex items-center gap-2 font-semibold">
              <CheckCircle2 className="h-5 w-5" /> Organization verified
            </div>
            <p className="mt-1">
              Organization <span className="font-mono">{orgId}</span> has completed onboarding.{" "}
              <Link href="/organization-admin/governance" className="underline">View in governance registry</Link>.
            </p>
          </section>
        )}
      </PageShell>
    </AppLayout>
  );
}
