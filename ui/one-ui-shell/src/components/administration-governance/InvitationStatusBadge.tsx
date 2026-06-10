"use client";

import {
  formatInvitationExpiry,
  invitationStatusLabel,
  invitationStatusTone,
  type InvitationView,
} from "@/lib/admin-governance/invitation-lifecycle";

const TONE_CLASS: Record<ReturnType<typeof invitationStatusTone>, string> = {
  neutral: "border-slate-200 bg-slate-50 text-slate-800",
  success: "border-emerald-200 bg-emerald-50 text-emerald-900",
  warning: "border-amber-200 bg-amber-50 text-amber-950",
  danger: "border-rose-200 bg-rose-50 text-rose-950",
  info: "border-indigo-200 bg-indigo-50 text-indigo-950",
};

interface InvitationStatusBadgeProps {
  invitation: InvitationView;
  compact?: boolean;
}

export function InvitationStatusBadge({ invitation, compact = false }: InvitationStatusBadgeProps) {
  const tone = invitationStatusTone(invitation.status);
  const expiry = formatInvitationExpiry(invitation.expiresAt);
  const showExpiredBadge = invitation.expired || invitation.status === "expired";

  return (
    <div className={`inline-flex flex-col gap-1 rounded-lg border px-2 py-1 text-xs ${TONE_CLASS[tone]}`}>
      <span className="font-semibold">{invitationStatusLabel(invitation.status)}</span>
      {!compact && invitation.auditStatus ? (
        <span className="opacity-80">Delivery audit: {invitation.auditStatus}</span>
      ) : null}
      {!compact && expiry ? <span className="opacity-80">Expires: {expiry}</span> : null}
      {showExpiredBadge ? <span className="font-medium">Expired</span> : null}
      {!compact && invitation.invitationId ? (
        <span className="opacity-70">Invitation ID: {invitation.invitationId}</span>
      ) : null}
    </div>
  );
}
