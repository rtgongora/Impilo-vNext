import Link from "next/link";
import { PublicShell } from "@/components/public/PublicShell";
import { FindCareExperience } from "@/components/public/find-care/FindCareExperience";

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
      <nav className="text-sm text-slate-500">
        <Link href="/welcome" className="hover:text-slate-900">
          Welcome
        </Link>{" "}
        / Find health services
      </nav>
      <h1 className="mt-2 text-3xl font-bold text-slate-900">Find health services near you</h1>
      <p className="mt-3 max-w-2xl text-slate-600">
        Tell us what care you need and we&apos;ll find facilities that actually offer it — no sign-in
        needed. To book an appointment or save a facility, create an account or sign in.
      </p>

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

      <div className="mt-8">
        <FindCareExperience />
      </div>

      <section className="mt-8 rounded-xl border border-slate-200 bg-white p-6">
        <h2 className="font-semibold text-slate-900">Visiting a facility</h2>
        <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-600">
          <li>Bring an identity document if you have one — it helps verify your Health ID faster.</li>
          <li>No Health ID yet? Staff can register you and issue a temporary Health ID on arrival.</li>
          <li>You control consent: you decide who can see your records.</li>
        </ul>
      </section>
    </PublicShell>
  );
}
