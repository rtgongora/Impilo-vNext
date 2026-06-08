"use client";

import { Building2, Shield, ShieldAlert } from "lucide-react";

export type ClientIntakeBadgeKind = "unverified" | "verified" | "facility-registered";

export interface ClientIntakeStatusInput {
  verificationStatus?: string | null;
  latestRegistrationType?: string | null;
  latestRegistrationChannel?: string | null;
  lifecycleStatus?: string | null;
  /** When present, ISSUED/DELIVERED issuance reinforces verified intake status. */
  issuanceState?: string | null;
}

const BADGE_STYLES: Record<ClientIntakeBadgeKind, string> = {
  unverified: "border border-gray-200 bg-gray-100 text-gray-700",
  verified: "border border-emerald-200 bg-emerald-50 text-emerald-800",
  "facility-registered": "border border-indigo-200 bg-indigo-50 text-indigo-800",
};

const BADGE_LABELS: Record<ClientIntakeBadgeKind, string> = {
  unverified: "Unverified",
  verified: "Verified",
  "facility-registered": "Facility registered",
};

function normalize(value: string | null | undefined) {
  return (value ?? "").trim().toUpperCase();
}

export function deriveClientIntakeBadges(input: ClientIntakeStatusInput): ClientIntakeBadgeKind[] {
  const verification = normalize(input.verificationStatus);
  const lifecycle = normalize(input.lifecycleStatus);
  const regType = normalize(input.latestRegistrationType);
  const channel = normalize(input.latestRegistrationChannel);
  const issuance = normalize(input.issuanceState);

  const issuanceVerified = issuance === "ISSUED" || issuance === "DELIVERED";
  const isVerified =
    verification === "VERIFIED" ||
    lifecycle === "VERIFIED" ||
    (lifecycle === "ACTIVE" && verification.includes("VERIFIED")) ||
    issuanceVerified;

  const isFacilityRegistered =
    regType === "FACILITY_REGISTRATION" ||
    regType.includes("FACILITY") ||
    channel.includes("FACILITY");

  const badges: ClientIntakeBadgeKind[] = [isVerified ? "verified" : "unverified"];
  if (isFacilityRegistered) {
    badges.push("facility-registered");
  }
  return badges;
}

function BadgeIcon({ kind }: { kind: ClientIntakeBadgeKind }) {
  if (kind === "verified") return <Shield className="h-3 w-3" aria-hidden />;
  if (kind === "facility-registered") return <Building2 className="h-3 w-3" aria-hidden />;
  return <ShieldAlert className="h-3 w-3" aria-hidden />;
}

export function ClientIntakeStatusBadges({
  input,
  className = "",
  testIdPrefix = "client-intake-badge",
}: {
  input: ClientIntakeStatusInput;
  className?: string;
  testIdPrefix?: string;
}) {
  const badges = deriveClientIntakeBadges(input);

  return (
    <span className={`inline-flex flex-wrap items-center gap-1.5 ${className}`}>
      {badges.map((kind) => (
        <span
          key={kind}
          data-testid={`${testIdPrefix}-${kind}`}
          className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-semibold ${BADGE_STYLES[kind]}`}
        >
          <BadgeIcon kind={kind} />
          {BADGE_LABELS[kind]}
        </span>
      ))}
    </span>
  );
}
