"use client";

import { useEffect, useState } from "react";
import { GovernanceActionResult } from "./GovernanceActionResult";
import {
  activateBootstrapAdmin,
  buildFirstAdminPayload,
  createFirstBootstrapAdmin,
  getBootstrapStatus,
  validateBootstrapToken,
  type BootstrapStatus,
} from "@/lib/admin-governance/api/bootstrapApi";
import type { AdminGovernanceActionResponse } from "@/lib/admin-governance/types";

export function FirstNationalAdministratorWizard() {
  const [status, setStatus] = useState<BootstrapStatus | null>(null);
  const [step, setStep] = useState(1);
  const [method, setMethod] = useState("one_time_token");
  const [token, setToken] = useState("");
  const [form, setForm] = useState<Record<string, string>>({
    fullName: "",
    officialEmail: "",
    phone: "",
    title: "",
    organisation: "Ministry of Health and Child Care",
    approvalReference: "",
  });
  const [password, setPassword] = useState("");
  const [mfaConfigured, setMfaConfigured] = useState(false);
  const [bootstrapAccountId, setBootstrapAccountId] = useState<string | null>(null);
  const [result, setResult] = useState<AdminGovernanceActionResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void getBootstrapStatus()
      .then(setStatus)
      .catch(() => setError("Bootstrap status unavailable."))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p className="text-sm text-slate-600">Checking bootstrap status…</p>;
  if (error) return <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-950">{error}</div>;
  if (!status?.bootstrapOpen && status?.activeNationalAdminExists) {
    return (
      <div className="rounded-xl border border-slate-200 bg-white px-4 py-6 text-sm text-slate-700">
        Bootstrap Mode is closed. Use Administration & Governance for routine platform operations.
      </div>
    );
  }

  async function handleValidateToken() {
    const validation = await validateBootstrapToken({ bootstrapMethod: method, bootstrapToken: token });
    if (validation.tokenValid) setStep(3);
    else setError("Bootstrap token invalid or expired.");
  }

  async function handleCreatePending() {
    const response = await createFirstBootstrapAdmin(buildFirstAdminPayload(form, method, token));
    setResult({
      status: (response.status ?? "pending") as AdminGovernanceActionResponse["status"],
      friendlyTitle: response.friendlyTitle ?? "Bootstrap pending",
      friendlyMessage: response.friendlyMessage ?? "Pending bootstrap account created.",
      allowedNextActions: [],
      bootstrapAccountId: response.bootstrapAccountId,
      auditStatus: response.auditStatus as AdminGovernanceActionResponse["auditStatus"],
      auditEventId: response.auditEventId,
    });
    if (response.bootstrapAccountId) {
      setBootstrapAccountId(response.bootstrapAccountId);
      setStep(4);
    }
  }

  async function handleActivate() {
    if (!bootstrapAccountId) return;
    const response = await activateBootstrapAdmin({ bootstrapAccountId, password, mfaConfigured });
    setResult({
      status: (response.status ?? "completed") as AdminGovernanceActionResponse["status"],
      friendlyTitle: response.friendlyTitle ?? "Bootstrap complete",
      friendlyMessage: response.friendlyMessage ?? "Bootstrap administrator activated.",
      allowedNextActions: [],
      bootstrapClosed: response.bootstrapClosed,
      auditStatus: response.auditStatus as AdminGovernanceActionResponse["auditStatus"],
    });
    setStep(5);
  }

  return (
    <div className="space-y-6">
      <div className="rounded-xl border border-indigo-100 bg-indigo-50/70 px-4 py-3 text-sm text-indigo-950">
        {status?.friendlyMessage}
      </div>

      {step === 1 ? (
        <section className="space-y-3">
          <h3 className="font-semibold text-slate-900">1. Bootstrap method</h3>
          {(status?.allowedBootstrapMethods ?? []).map((item) => (
            <label key={item} className="flex items-center gap-2 text-sm">
              <input type="radio" checked={method === item} onChange={() => setMethod(item)} />
              {item.replace(/_/g, " ")}
            </label>
          ))}
          <button type="button" className="rounded-lg bg-indigo-700 px-4 py-2 text-sm text-white" onClick={() => setStep(2)}>
            Continue
          </button>
        </section>
      ) : null}

      {step === 2 ? (
        <section className="space-y-3">
          <h3 className="font-semibold text-slate-900">2. Verify bootstrap credential</h3>
          <input className="w-full rounded-lg border px-3 py-2 text-sm" placeholder="Bootstrap token" value={token} onChange={(e) => setToken(e.target.value)} />
          <button type="button" className="rounded-lg bg-indigo-700 px-4 py-2 text-sm text-white" onClick={() => void handleValidateToken()}>
            Validate token
          </button>
        </section>
      ) : null}

      {step >= 3 && step < 4 ? (
        <section className="grid gap-3 md:grid-cols-2">
          <h3 className="md:col-span-2 font-semibold text-slate-900">3. Nominated first national administrator</h3>
          {[
            ["fullName", "Full name"],
            ["officialEmail", "Official email"],
            ["phone", "Phone"],
            ["title", "Title / position"],
            ["approvalReference", "Approval reference"],
          ].map(([name, label]) => (
            <label key={name} className="text-sm">
              {label}
              <input className="mt-1 w-full rounded-lg border px-3 py-2" value={form[name] ?? ""} onChange={(e) => setForm((prev) => ({ ...prev, [name]: e.target.value }))} />
            </label>
          ))}
          <button type="button" className="rounded-lg bg-indigo-700 px-4 py-2 text-sm text-white md:col-span-2 md:w-fit" onClick={() => void handleCreatePending()}>
            Create pending bootstrap account
          </button>
        </section>
      ) : null}

      {step === 4 ? (
        <section className="space-y-3">
          <h3 className="font-semibold text-slate-900">4. Activate with MFA</h3>
          <input className="w-full rounded-lg border px-3 py-2 text-sm" type="password" placeholder="Set password" value={password} onChange={(e) => setPassword(e.target.value)} />
          <label className="flex items-center gap-2 text-sm">
            <input type="checkbox" checked={mfaConfigured} onChange={(e) => setMfaConfigured(e.target.checked)} />
            MFA configured
          </label>
          <button type="button" className="rounded-lg bg-indigo-700 px-4 py-2 text-sm text-white" onClick={() => void handleActivate()}>
            Activate and close bootstrap
          </button>
        </section>
      ) : null}

      {result ? <GovernanceActionResult result={result} /> : null}
    </div>
  );
}
