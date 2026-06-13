"use client";

import { usePathname } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { GovernanceActionResult } from "./GovernanceActionResult";
import { InvitationLifecycleActions } from "./InvitationLifecycleActions";
import { InvitationStatusBadge } from "./InvitationStatusBadge";
import {
  getAuthorisedRepresentativeInvitationAuditTrail,
  inviteAuthorisedRepresentative,
  listAuthorisedRepresentatives,
  resendAuthorisedRepresentativeInvitation,
  revokeAuthorisedRepresentative,
  revokeAuthorisedRepresentativeInvitation,
  suspendAuthorisedRepresentative,
} from "@/lib/admin-governance/api/authorisedRepresentativesApi";
import { isActionResponse, isPendingBackend } from "@/lib/admin-governance/api/client";
import { normalizeInvitationView } from "@/lib/admin-governance/invitation-lifecycle";
import type { AdminGovernanceActionResponse, LookupEnvelope } from "@/lib/admin-governance/types";

export function AuthorisedRepresentativesPanel() {
  const pathname = usePathname();
  const orgMatch = pathname.match(/\/organisations\/([^/]+)/);
  const orgId = orgMatch && orgMatch[1] !== "new" ? orgMatch[1] : null;
  const [items, setItems] = useState<Record<string, unknown>[]>([]);
  const [form, setForm] = useState({ userId: "", roleTemplateId: "organisation_authorised_representative" });
  const [result, setResult] = useState<AdminGovernanceActionResponse | null>(null);
  const [auditTrail, setAuditTrail] = useState<Record<string, unknown>[] | null>(null);

  const refresh = useCallback(async () => {
    if (!orgId) return;
    const response = await listAuthorisedRepresentatives(orgId);
    if (!isActionResponse(response)) {
      setItems((response as LookupEnvelope<{ items: Record<string, unknown>[] }>).data?.items ?? []);
    }
  }, [orgId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  if (!orgId) return null;

  async function handleInvite() {
    const response = await inviteAuthorisedRepresentative(orgId!, form);
    if (isActionResponse(response)) {
      setResult(response);
      await refresh();
    }
  }

  async function handleResend(repId: string) {
    const response = await resendAuthorisedRepresentativeInvitation(orgId!, repId);
    setResult(response);
    await refresh();
  }

  async function handleRevokeInvitation(repId: string) {
    const response = await revokeAuthorisedRepresentativeInvitation(orgId!, repId);
    setResult(response);
    await refresh();
  }

  async function handleViewAudit(repId: string) {
    const response = await getAuthorisedRepresentativeInvitationAuditTrail(orgId!, repId);
    if (!isActionResponse(response)) {
      setAuditTrail((response as LookupEnvelope<{ auditTrail: Record<string, unknown>[] }>).data?.auditTrail ?? []);
    }
  }

  return (
    <section className="space-y-4 rounded-2xl border border-border bg-card p-5 shadow-sm">
      <h3 className="text-base font-semibold text-foreground">Authorised Representatives</h3>
      <p className="text-sm text-muted-foreground">
        Organisation-scoped delegates who may upload authoritative data. Invitations remain staged until identity confirmation — spreadsheet rows never auto-activate users.
      </p>
      <div className="grid gap-3 md:grid-cols-2">
        <input className="rounded-lg border px-3 py-2 text-sm" placeholder="User ID / email" value={form.userId} onChange={(e) => setForm((prev) => ({ ...prev, userId: e.target.value }))} />
        <input className="rounded-lg border px-3 py-2 text-sm" placeholder="Role template" value={form.roleTemplateId} onChange={(e) => setForm((prev) => ({ ...prev, roleTemplateId: e.target.value }))} />
      </div>
      <button type="button" className="rounded-lg bg-primary px-4 py-2 text-sm text-white" onClick={() => void handleInvite()}>
        Invite authorised representative
      </button>
      <ul className="space-y-3 text-sm">
        {items.map((item) => {
          const invitation = normalizeInvitationView(item.invitation as Record<string, unknown> | undefined);
          const repId = String(item.id);
          return (
            <li key={repId} className="rounded-lg border border-border px-3 py-3">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="font-medium text-foreground">{String(item.userId)}</p>
                  <p className="text-xs text-muted-foreground">
                    {String(item.roleTemplateId ?? item.representativeType)} · Representative status: {String(item.status)}
                  </p>
                </div>
                <InvitationStatusBadge invitation={invitation} />
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                <button type="button" className="text-xs underline" onClick={() => void suspendAuthorisedRepresentative(orgId!, repId).then(setResult)}>
                  Suspend representative
                </button>
                <button type="button" className="text-xs underline" onClick={() => void revokeAuthorisedRepresentative(orgId!, repId).then(setResult)}>
                  Revoke representative
                </button>
              </div>
              <div className="mt-2">
                <InvitationLifecycleActions
                  invitation={invitation}
                  onResend={() => handleResend(repId)}
                  onRevoke={() => handleRevokeInvitation(repId)}
                  onViewAudit={() => void handleViewAudit(repId)}
                />
              </div>
            </li>
          );
        })}
      </ul>
      {auditTrail ? (
        <pre className="overflow-auto rounded-lg bg-background p-3 text-xs">{JSON.stringify(auditTrail, null, 2)}</pre>
      ) : null}
      {result ? <GovernanceActionResult result={result} /> : null}
    </section>
  );
}
