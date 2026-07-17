import Link from "next/link";
import { PublicShell } from "@/components/public/PublicShell";
import { FindCareFacilityDetail } from "@/components/public/find-care/FindCareFacilityDetail";

export const metadata = {
  title: "Facility details — Impilo",
  description:
    "See the services a health facility offers, how to get there, and how to verify a health professional. No sign-in needed.",
};

/**
 * Public facility detail page (gateway pillar: Get care / Find a service).
 *
 * Disclosure-limited profile from the anonymous find-care lane: services offered,
 * operating hours (honest — shown as unverified when absent), coordinates and a
 * directions link, and a real "verify a professional" next step. No PII, no
 * hard-coded facility truth.
 */
export default function FindCareFacilityPage({ params }: { params: { facilityId: string } }) {
  return (
    <PublicShell>
      <nav className="text-sm text-slate-500">
        <Link href="/welcome" className="hover:text-slate-900">
          Welcome
        </Link>{" "}
        /{" "}
        <Link href="/welcome/find-care" className="hover:text-slate-900">
          Find health services
        </Link>{" "}
        / Facility
      </nav>
      <div className="mt-4">
        <FindCareFacilityDetail facilityId={params.facilityId} />
      </div>
    </PublicShell>
  );
}
