"use client";

import { useState } from "react";
import type { InvitationView } from "@/lib/admin-governance/invitation-lifecycle";
import { shouldShowRetry } from "@/lib/admin-governance/invitation-lifecycle";

interface InvitationLifecycleActionsProps {
  invitation: InvitationView;
  onResend?: () => Promise<void>;
  onRevoke?: () => Promise<void>;
  onViewAudit?: () => void;
  disabled?: boolean;
}

export function InvitationLifecycleActions({
  invitation,
  onResend,
  onRevoke,
  onViewAudit,
  disabled = false,
}: InvitationLifecycleActionsProps) {
  const [busy, setBusy] = useState(false);

  async function run(action?: () => Promise<void>) {
    if (!action || disabled || busy) return;
    setBusy(true);
    try {
      await action();
    } finally {
      setBusy(false);
    }
  }

  const canResend = Boolean(onResend) && shouldShowRetry(invitation);
  const canRevoke = Boolean(onRevoke) && Boolean(invitation.canRevoke) && invitation.status !== "revoked" && invitation.status !== "activated";

  return (
    <div className="flex flex-wrap gap-2">
      {canResend ? (
        <button type="button" className="text-xs underline" disabled={busy || disabled} onClick={() => void run(onResend)}>
          {invitation.status === "failed" || invitation.status === "pending_backend" ? "Retry delivery" : "Resend invitation"}
        </button>
      ) : null}
      {canRevoke ? (
        <button type="button" className="text-xs underline text-danger" disabled={busy || disabled} onClick={() => void run(onRevoke)}>
          Revoke invitation
        </button>
      ) : null}
      {onViewAudit ? (
        <button type="button" className="text-xs underline" disabled={busy || disabled} onClick={onViewAudit}>
          View invitation audit trail
        </button>
      ) : null}
      {invitation.status === "revoked" ? (
        <span className="text-xs text-danger">Revoked invitations cannot be used for activation.</span>
      ) : null}
    </div>
  );
}
