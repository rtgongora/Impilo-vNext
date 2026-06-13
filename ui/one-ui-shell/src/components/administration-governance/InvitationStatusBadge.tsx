"use client";

import {
  formatInvitationExpiry,
  invitationStatusLabel,
  invitationStatusTone,
  type InvitationView,
} from "@/lib/admin-governance/invitation-lifecycle";

const TONE_CLASS: Record<ReturnType<typeof invitationStatusTone>, string> = {
  neutral: "border-border bg-neutral-100 text-foreground",
  success: "border-success/25 bg-success-soft text-primary-hover",
  warning: "border-warning/35 bg-warning-soft text-warning-foreground",
  danger: "border-danger/28 bg-danger-soft text-danger",
  info: "border-info/25 bg-info-soft text-primary-hover",
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
