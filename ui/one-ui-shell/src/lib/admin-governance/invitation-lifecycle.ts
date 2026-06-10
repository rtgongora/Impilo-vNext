export type InvitationLifecycleStatus =
  | "not_sent"
  | "pending_backend"
  | "sent"
  | "failed"
  | "expired"
  | "revoked"
  | "activated";

export interface InvitationView {
  status: InvitationLifecycleStatus;
  invitationId?: string | null;
  keycloakUserId?: string | null;
  auditStatus?: string | null;
  expiresAt?: string | null;
  expired?: boolean;
  canResend?: boolean;
  canRevoke?: boolean;
  canRetry?: boolean;
  auditTrail?: Array<Record<string, unknown>>;
}

export const INVITATION_STATUS_LABELS: Record<InvitationLifecycleStatus, string> = {
  not_sent: "Not sent",
  pending_backend: "Pending backend",
  sent: "Sent",
  failed: "Failed",
  expired: "Expired",
  revoked: "Revoked",
  activated: "Activated",
};

export function normalizeInvitationView(raw?: Record<string, unknown> | null): InvitationView {
  if (!raw) {
    return { status: "not_sent", canResend: false, canRevoke: false, canRetry: false, expired: false };
  }
  return {
    status: (raw.status as InvitationLifecycleStatus) ?? "not_sent",
    invitationId: (raw.invitationId as string | null | undefined) ?? null,
    keycloakUserId: (raw.keycloakUserId as string | null | undefined) ?? null,
    auditStatus: (raw.auditStatus as string | null | undefined) ?? null,
    expiresAt: (raw.expiresAt as string | null | undefined) ?? null,
    expired: Boolean(raw.expired),
    canResend: Boolean(raw.canResend),
    canRevoke: Boolean(raw.canRevoke),
    canRetry: Boolean(raw.canRetry),
    auditTrail: Array.isArray(raw.auditTrail) ? (raw.auditTrail as Array<Record<string, unknown>>) : [],
  };
}

export function invitationStatusLabel(status: InvitationLifecycleStatus): string {
  return INVITATION_STATUS_LABELS[status] ?? status;
}

export function invitationStatusTone(status: InvitationLifecycleStatus): "neutral" | "success" | "warning" | "danger" | "info" {
  switch (status) {
    case "sent":
    case "activated":
      return "success";
    case "pending_backend":
    case "not_sent":
      return "warning";
    case "failed":
    case "expired":
    case "revoked":
      return "danger";
    default:
      return "neutral";
  }
}

export function formatInvitationExpiry(expiresAt?: string | null): string | null {
  if (!expiresAt) return null;
  const parsed = Date.parse(expiresAt);
  if (Number.isNaN(parsed)) return expiresAt;
  return new Date(parsed).toLocaleString();
}

export function invitationIsUsable(invitation: InvitationView): boolean {
  return invitation.status !== "revoked" && invitation.status !== "failed" && !invitation.expired;
}

export function shouldShowRetry(invitation: InvitationView): boolean {
  return Boolean(invitation.canRetry || invitation.canResend) && invitation.status !== "revoked" && invitation.status !== "activated";
}
