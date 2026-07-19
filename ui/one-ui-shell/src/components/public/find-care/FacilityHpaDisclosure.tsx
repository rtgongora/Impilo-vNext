/**
 * Honest regulatory-source disclosure for a public facility profile (HPA
 * enrichment). Regulator-listed records may be searchable while incomplete — this
 * block states the source and its date, the verification state, and what is still
 * awaiting confirmation, and never implies that a listing proves the facility is
 * currently open. Copy mirrors the bundle public-display contract.
 *
 * The anonymous find-care lane cannot itself mutate anything, so the completion
 * actions LINK to the authenticated claim/confirm flow rather than call it.
 */

import Link from "next/link";
import { AlertTriangle, BadgeCheck, MapPinOff, PhoneCall, ShieldQuestion } from "lucide-react";
import type { FacilityProfile } from "@/lib/find-care/types";

function formatSourceDate(iso: string | null | undefined): string | null {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleDateString(undefined, { year: "numeric", month: "long", day: "numeric" });
}

const VERIFICATION_LABEL: Record<string, string> = {
  REGULATOR_LISTED: "Regulator listed",
  REGULATOR_VERIFIED: "Regulator verified",
  FACILITY_CLAIMED: "Claimed by the facility",
  LOCATION_VERIFIED: "Location verified",
  SERVICES_VERIFIED: "Services verified",
  OPERATIONALLY_VALIDATED: "Operationally validated",
};

export function FacilityHpaDisclosure({ profile }: { profile: FacilityProfile }) {
  const isRegulatorListed = (profile.sourceSystem ?? "").toUpperCase() === "HPA"
    || !!profile.verificationStatus;
  if (!isRegulatorListed) {
    return null;
  }

  const sourceDate = formatSourceDate(profile.sourceEffectiveDate);
  const verification = profile.verificationStatus
    ? VERIFICATION_LABEL[profile.verificationStatus] ?? profile.verificationStatus
    : "Regulator listed";
  const noCoordinates = profile.latitude == null || profile.longitude == null;

  return (
    <section
      className="rounded-2xl border border-amber-200 bg-amber-50/60 p-6"
      data-testid="facility-hpa-disclosure"
    >
      <h2 className="flex items-center gap-2 font-semibold text-slate-900">
        <ShieldQuestion className="h-4 w-4 text-amber-600" aria-hidden />
        About this listing
      </h2>

      <div className="mt-3 flex flex-wrap items-center gap-2">
        <span className="inline-flex items-center gap-1.5 rounded-full bg-white px-2.5 py-1 text-xs font-medium text-slate-700 ring-1 ring-slate-200">
          <BadgeCheck className="h-3.5 w-3.5 text-emerald-600" aria-hidden />
          {verification}
        </span>
        {profile.profileIncomplete && (
          <span className="rounded-full bg-white px-2.5 py-1 text-xs text-slate-600 ring-1 ring-slate-200">
            Some facility information is still required
          </span>
        )}
      </div>

      <p className="mt-3 text-sm text-slate-700">
        {sourceDate
          ? `Listed in HPA regulatory information as of ${sourceDate}.`
          : "Listed in HPA regulatory information."}{" "}
        A regulatory listing does not confirm that this facility is currently open or operational.
      </p>

      {/* Honest "awaiting confirmation" prompts. */}
      <ul className="mt-3 space-y-1.5 text-sm text-slate-600">
        {noCoordinates && (
          <li className="flex items-start gap-1.5">
            <MapPinOff className="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-500" aria-hidden />
            Map location awaiting confirmation.
          </li>
        )}
        <li className="flex items-start gap-1.5">
          <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-500" aria-hidden />
          Operating hours have not yet been confirmed.
        </li>
        <li className="flex items-start gap-1.5">
          <PhoneCall className="mt-0.5 h-3.5 w-3.5 shrink-0 text-amber-500" aria-hidden />
          Public contact details awaiting confirmation — call before travelling.
        </li>
      </ul>

      {/* Completion actions — link to the authenticated claim/confirm flow. */}
      <div className="mt-4 flex flex-wrap gap-2">
        <Link
          href="/facility/claim"
          className="inline-flex items-center rounded-lg bg-slate-900 px-3.5 py-2 text-sm font-semibold text-white hover:bg-slate-800"
        >
          Claim this facility
        </Link>
        <Link
          href="/facility/claim"
          className="inline-flex items-center rounded-lg border border-slate-300 bg-white px-3.5 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50"
        >
          Suggest a correction
        </Link>
      </div>
    </section>
  );
}
