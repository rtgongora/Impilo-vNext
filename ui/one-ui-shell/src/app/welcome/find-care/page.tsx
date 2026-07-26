import Link from "next/link";
import { PublicShell } from "@/components/public/PublicShell";
import { PublicPageHeader } from "@/components/public/PublicPrimitives";
import { FindCareExperience } from "@/components/public/find-care/FindCareExperience";
import { NompiloAdaptiveGuidance } from "@/components/intelligent/NompiloAdaptiveGuidance";

export const metadata = {
  title: "Find health services — Impilo",
  description:
    "Say what care you need — maternity, X-ray, HIV medicines, dialysis and more — and find facilities that actually offer it, near you. No sign-in needed.",
};

/**
 * Public "Find Health Services" page (gateway pillar: Get care / Find a service).
 *
 * A need-first, service-aware access-to-care search over the anonymous gateway
 * lane: a person asks in plain language, optionally shares a location, and gets
 * unified result cards ranked by service-match then nearness. No facility or
 * provider truth is hard-coded on this page — every result is registry-derived
 * via the BFF. Booking and saved facilities remain behind sign-in. No PII here.
 */
export default function FindCarePage() {
  return (
    <PublicShell>
      <PublicPageHeader
        crumbs={[{ label: "Impilo", href: "/" }, { label: "Find health services" }]}
        title="Find health services near you"
        lede={
          <>
            Tell us what care you need and we&apos;ll find facilities that actually offer it — no
            sign-in needed. To book an appointment or save a facility, create an account or sign in.
          </>
        }
      />

      <div className="mt-6 flex flex-wrap gap-3">
        <Link
          href="/verify/facility-certificate"
          className="rounded-lg border border-slate-300 bg-white px-5 py-2.5 font-semibold text-slate-800 hover:bg-slate-50"
        >
          Verify a facility certificate
        </Link>
        <Link
          href="/verify/practitioner"
          className="rounded-lg border border-slate-300 bg-white px-5 py-2.5 font-semibold text-slate-800 hover:bg-slate-50"
        >
          Verify a health professional
        </Link>
        <Link
          href="/welcome/emergency"
          className="rounded-lg border border-red-300 bg-white px-5 py-2.5 font-semibold text-red-700 hover:bg-red-50"
        >
          Emergency information
        </Link>
      </div>

      {/* Nompilo companion on the active journey (doctrine §11). Advisory mode: it states
          what this search can and cannot tell you, which a person should not miss, so it
          does not auto-collapse. Renders in flow — never over the search or its actions. */}
      <NompiloAdaptiveGuidance
        id="find-care-guidance"
        mode="advisory"
        collapsedLabel="What can this search tell me?"
        contextMessage="Tell me what care you need in ordinary words — a service like maternity or X-ray, or a need like 'somewhere to get insulin'. Sharing your location is optional and only sorts results by distance."
        suggestions={[
          "Results come from the national facility register, so a listing means the service is recorded — not that it is open right now.",
          "Opening hours are not a live signal, so check by phone before travelling.",
          "You can search, compare and get directions without signing in. Signing in is only needed to book or save a facility.",
        ]}
      />

      <div className="mt-8">
        <FindCareExperience />
      </div>

      <section className="mt-8 rounded-xl border border-slate-200 bg-white p-6">
        <h2 className="font-semibold text-slate-900">Visiting a facility</h2>
        <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-600">
          <li>Bring an identity document if you have one — it helps verify your Impilo ID faster.</li>
          <li>No Impilo ID yet? Staff can register you and issue a temporary Impilo ID on arrival.</li>
          <li>You control consent: you decide who can see your records.</li>
        </ul>
      </section>
    </PublicShell>
  );
}
