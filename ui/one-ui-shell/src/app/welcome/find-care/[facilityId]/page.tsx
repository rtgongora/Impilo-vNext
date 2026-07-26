import { PublicShell } from "@/components/public/PublicShell";
import { PublicPageHeader } from "@/components/public/PublicPrimitives";
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
      {/* The only chrome this page had was a bare breadcrumb; the shared header gives it
          the same preamble as every other public journey. */}
      <PublicPageHeader
        crumbs={[
          { label: "Impilo", href: "/" },
          { label: "Find health services", href: "/welcome/find-care" },
          { label: "Facility" },
        ]}
        title="Facility details"
        lede="Services offered, how to get there, and how to verify a health professional — no sign-in needed."
      />
      <div className="mt-6">
        <FindCareFacilityDetail facilityId={params.facilityId} />
      </div>
    </PublicShell>
  );
}
