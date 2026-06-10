"use client";

import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { GovernanceActionResult } from "./GovernanceActionResult";
import {
  inviteAuthorisedRepresentative,
  listAuthorisedRepresentatives,
  revokeAuthorisedRepresentative,
  suspendAuthorisedRepresentative,
} from "@/lib/admin-governance/api/authorisedRepresentativesApi";
import { isActionResponse, isPendingBackend } from "@/lib/admin-governance/api/client";
import type { AdminGovernanceActionResponse, LookupEnvelope } from "@/lib/admin-governance/types";

export function AuthorisedRepresentativesPanel() {
  const pathname = usePathname();
  const orgMatch = pathname.match(/\/organisations\/([^/]+)/);
  const orgId = orgMatch && orgMatch[1] !== "new" ? orgMatch[1] : null;
  const [items, setItems] = useState<Record<string, unknown>[]>([]);
  const [form, setForm] = useState({ userId: "", roleTemplateId: "organisation_authorised_representative" });
  const [result, setResult] = useState<AdminGovernanceActionResponse | null>(null);

  useEffect(() => {
    if (!orgId) return;
    void listAuthorisedRepresentatives(orgId).then((response) => {
      if (!isActionResponse(response)) setItems((response as LookupEnvelope<{ items: Record<string, unknown>[] }>).data?.items ?? []);
    });
  }, [orgId]);

  if (!orgId) return null;

  async function handleInvite() {
    const response = await inviteAuthorisedRepresentative(orgId!, form);
    if (isActionResponse(response)) setResult(response);
  }

  return (
    <section className="space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <h3 className="text-base font-semibold text-slate-900">Authorised Representatives</h3>
      <p className="text-sm text-slate-600">Organisation-scoped delegates who may upload authoritative data. They cannot self-escalate or grant trust tier changes.</p>
      <div className="grid gap-3 md:grid-cols-2">
        <input className="rounded-lg border px-3 py-2 text-sm" placeholder="User ID / email" value={form.userId} onChange={(e) => setForm((prev) => ({ ...prev, userId: e.target.value }))} />
        <input className="rounded-lg border px-3 py-2 text-sm" placeholder="Role template" value={form.roleTemplateId} onChange={(e) => setForm((prev) => ({ ...prev, roleTemplateId: e.target.value }))} />
      </div>
      <button type="button" className="rounded-lg bg-indigo-700 px-4 py-2 text-sm text-white" onClick={() => void handleInvite()}>
        Invite authorised representative
      </button>
      <ul className="space-y-2 text-sm">
        {items.map((item) => (
          <li key={String(item.id)} className="rounded-lg border border-slate-100 px-3 py-2">
            {String(item.userId)} · {String(item.roleTemplateId ?? item.representativeType)} · {String(item.status)}
            <div className="mt-2 flex gap-2">
              <button type="button" className="text-xs underline" onClick={() => void suspendAuthorisedRepresentative(orgId!, String(item.id)).then(setResult)}>
                Suspend
              </button>
              <button type="button" className="text-xs underline" onClick={() => void revokeAuthorisedRepresentative(orgId!, String(item.id)).then(setResult)}>
                Revoke
              </button>
            </div>
          </li>
        ))}
      </ul>
      {result ? <GovernanceActionResult result={result} /> : null}
    </section>
  );
}
