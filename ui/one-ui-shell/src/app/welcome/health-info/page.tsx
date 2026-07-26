import Link from "next/link";
import { PublicShell } from "@/components/public/PublicShell";
import { PublicPageHeader } from "@/components/public/PublicPrimitives";
import { PublicHealthInfo } from "@/components/public/PublicHealthInfo";

export const metadata = {
  title: "Health information — Impilo",
  description:
    "Trusted, plain-language health information: when to seek care, mother and child health, vaccinations, chronic conditions, mental health, medicines safety, prevention and outbreak notices. No sign-in needed.",
};

/**
 * Public health-information page (gateway pillar: Health information). Trusted
 * public-health knowledge is readable WITHOUT sign-in via the anonymous gateway lane;
 * personalised education, reminders and saved content remain behind sign-in.
 * No personal or health data appears on this page.
 */
export default function HealthInfoPage() {
  return (
    <PublicShell>
      <PublicPageHeader
        crumbs={[{ label: "Impilo", href: "/" }, { label: "Health information" }]}
        title="Health information"
        lede={
          <>
            Plain-language health knowledge you can trust — when to seek care, keeping children
            healthy, living with chronic conditions, using medicines safely, and preventing
            illness. Read freely; no account is needed.
          </>
        }
      />

      <div className="mt-6 flex flex-wrap gap-3">
        <Link
          href="/welcome/find-care"
          className="rounded-lg border border-slate-300 bg-white px-5 py-2.5 font-semibold text-slate-800 hover:bg-slate-50"
        >
          Find care near you
        </Link>
        <Link
          href="/welcome/emergency"
          className="rounded-lg border border-red-300 bg-white px-5 py-2.5 font-semibold text-red-700 hover:bg-red-50"
        >
          Emergency information
        </Link>
      </div>

      <div className="mt-8">
        <PublicHealthInfo />
      </div>

      <section className="mt-8 rounded-xl border border-slate-200 bg-white p-6">
        <h2 className="font-semibold text-slate-900">About this information</h2>
        <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-600">
          <li>
            Content is published by the Ministry of Health &amp; Child Care in plain language and
            is general information — it does not replace advice from a health worker.
          </li>
          <li>
            Official outbreak notices appear under the &ldquo;Outbreak notices&rdquo; topic when
            issued. Always rely on the most recent official communication.
          </li>
          <li>
            More topics are added over time. Personalised education and reminders are available
            after you sign in.
          </li>
        </ul>
      </section>
    </PublicShell>
  );
}
