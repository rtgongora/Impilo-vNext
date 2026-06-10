/**
 * Mobile parity — aligned with contracts/trust session experience doctrine.
 */

export type LoginMethod = "health_id" | "provider_id" | "email" | "phone" | "sso" | "unknown";

export interface MobileWorkAssignment {
  assignmentId: string;
  assignmentStatus: string;
  contextType?: string;
}

const ACTIVE = new Set(["active", "active_temporary", "active_restricted"]);

export function resolveMobileIdentityContext(input: {
  user: {
    id: string;
    providerActivated?: boolean;
    providerId?: string;
    linkedProviderId?: string;
    loginMethod?: LoginMethod;
    providerStatus?: string | null;
  } | null;
  workAssignments?: MobileWorkAssignment[];
}): {
  isCitizenOnly: boolean;
  hasProfessionalAccess: boolean;
  hasWorkAccess: boolean;
  defaultLandingPath: string;
} {
  const user = input.user;
  const providerId = user?.providerId ?? user?.linkedProviderId;
  const status = user?.providerStatus?.toLowerCase().replace(/[\s-]+/g, "_");
  const professionalStatuses = new Set(["verified", "active", "active_restricted"]);
  const hasProfessionalAccess =
    !!(providerId || (user?.loginMethod === "provider_id" && user?.linkedProviderId)) &&
    (!status || professionalStatuses.has(status));

  const activeAssignments = (input.workAssignments ?? []).filter((a) =>
    ACTIVE.has(a.assignmentStatus.toLowerCase().replace(/[\s-]+/g, "_")),
  );
  const blocked = new Set(["suspended", "expired", "revoked"]);
  const hasWorkAccess =
    hasProfessionalAccess &&
    activeAssignments.length > 0 &&
    !(status && blocked.has(status));

  let defaultLandingPath = "/home";
  if (hasWorkAccess) defaultLandingPath = activeAssignments.length > 1 ? "/auth/context-chooser" : "/facility";
  else if (hasProfessionalAccess) defaultLandingPath = "/professional";

  return {
    isCitizenOnly: !hasProfessionalAccess,
    hasProfessionalAccess,
    hasWorkAccess,
    defaultLandingPath,
  };
}
