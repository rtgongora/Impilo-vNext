"use client";

import { useState } from "react";
import { GovernanceActionResult } from "./GovernanceActionResult";
import { useDomainGovernanceAction } from "@/hooks/useAdminGovernance";
import type { AdminGovernanceActionResponse } from "@/lib/admin-governance/types";

const DOMAIN_ACTIONS: Record<string, { action: string; label: string; fields: Array<{ name: string; label: string; placeholder?: string }> }> = {
  hsc: {
    action: "HSC_EMPLOYMENT_UPDATE",
    label: "Update public-sector employment record",
    fields: [
      { name: "targetSubjectId", label: "Provider / Worker ID", placeholder: "P-..." },
      { name: "requestedScope", label: "Employment status", placeholder: "active | suspended_from_employment" },
      { name: "organisationId", label: "Posting facility / office" },
    ],
  },
  regulators: {
    action: "REGULATOR_PROVIDER_ACTION",
    label: "Regulatory provider action",
    fields: [
      { name: "organisationId", label: "Council / regulator ID" },
      { name: "targetSubjectId", label: "Provider / Worker ID" },
      { name: "requestedScope", label: "Action", placeholder: "verify_licence | apply_restriction" },
    ],
  },
  madi_admin: {
    action: "MADI_USER_CREATE",
    label: "Add blood service user",
    fields: [
      { name: "organisationId", label: "Blood service organisation" },
      { name: "targetSubjectId", label: "User / provider ID" },
      { name: "roleTemplate", label: "Madi role", placeholder: "blood_bank_manager" },
    ],
  },
  marketplace_orgs: {
    action: "MARKETPLACE_USER_CREATE",
    label: "Add marketplace organisation user",
    fields: [
      { name: "organisationId", label: "Vendor organisation ID" },
      { name: "targetSubjectId", label: "User ID" },
      { name: "requestedEnvironment", label: "Environment", placeholder: "sandbox | production_limited" },
    ],
  },
  payers: {
    action: "PAYER_USER_CREATE",
    label: "Add payer user",
    fields: [
      { name: "organisationId", label: "Payer organisation ID" },
      { name: "targetSubjectId", label: "User ID" },
      { name: "requestedDataScope", label: "Data scope", placeholder: "claims_only | eligibility_only" },
    ],
  },
  partners: {
    action: "PARTNER_USER_CREATE",
    label: "Add external partner user",
    fields: [
      { name: "organisationId", label: "Partner organisation ID" },
      { name: "targetSubjectId", label: "User ID" },
      { name: "requestedDataScope", label: "Data scope", placeholder: "aggregate_only" },
      { name: "requestedDurationDays", label: "Duration (days)" },
    ],
  },
  system_admin: {
    action: "ONBOARD_SYSTEM_ADMIN",
    label: "Scoped system admin assignment",
    fields: [
      { name: "targetSubjectId", label: "Administrator user ID" },
      { name: "roleTemplate", label: "Admin type", placeholder: "trust_administrator" },
      { name: "requestedScope", label: "Scope", placeholder: "national | province | facility" },
    ],
  },
};

interface DomainGovernancePanelProps {
  surfaceId: string;
  sourcePage: string;
}

export function DomainGovernancePanel({ surfaceId, sourcePage }: DomainGovernancePanelProps) {
  const config = DOMAIN_ACTIONS[surfaceId];
  const [form, setForm] = useState<Record<string, string>>({});
  const [result, setResult] = useState<AdminGovernanceActionResponse | null>(null);
  const actionMutation = useDomainGovernanceAction(config?.action ?? "DOMAIN_ACTION");

  if (!config) return null;

  async function handleSubmit() {
    const body: Record<string, unknown> = {
      ...form,
      sourcePage,
      requestedDurationDays: form.requestedDurationDays ? Number(form.requestedDurationDays) : undefined,
    };
    const response = await actionMutation.mutateAsync(body);
    setResult(response);
  }

  return (
    <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <div>
        <h3 className="text-base font-semibold text-slate-900">{config.label}</h3>
        <p className="mt-1 text-sm text-slate-600">Contract-wired action with OPA precheck and audit envelope.</p>
      </div>
      <div className="grid gap-3 md:grid-cols-2">
        {config.fields.map((field) => (
          <label key={field.name} className="text-sm text-slate-700">
            {field.label}
            <input
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
              placeholder={field.placeholder}
              value={form[field.name] ?? ""}
              onChange={(event) => setForm((prev) => ({ ...prev, [field.name]: event.target.value }))}
            />
          </label>
        ))}
      </div>
      {result ? <GovernanceActionResult result={result} /> : null}
      <button
        type="button"
        disabled={actionMutation.isPending}
        onClick={() => void handleSubmit()}
        className="rounded-lg bg-indigo-700 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-800 disabled:opacity-50"
      >
        {actionMutation.isPending ? "Submitting…" : "Submit governed action"}
      </button>
    </section>
  );
}
