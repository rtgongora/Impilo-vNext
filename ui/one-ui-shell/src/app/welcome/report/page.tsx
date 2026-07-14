import Link from "next/link";
import { PublicShell } from "@/components/public/PublicShell";
import { ReportIncidentTriage } from "@/components/public/ReportIncidentTriage";

export const metadata = {
  title: "Report a health incident — Impilo",
  description:
    "Report an emergency, unsafe care, a safety concern or a complaint. Emergency help is never blocked by sign-in.",
};

/**
 * Public "Report a health incident" triage (gateway doctrine §2 — cards attach to the
 * emergency-help and feedback-complaints pillars). Reachable WITHOUT login; routes each
 * branch honestly: emergencies go straight to the always-open emergency surface, safety
 * concerns and complaints continue into the signed-in feedback journey until the
 * anonymous public intake lane lands.
 */
export default function ReportIncidentPage() {
  return (
    <PublicShell>
      <nav className="text-sm text-slate-500">
        <Link href="/welcome" className="hover:text-slate-900">
          Welcome
        </Link>{" "}
        / Report a health incident
      </nav>

      <section className="mt-3 rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="text-3xl font-bold text-slate-900">Report a health incident</h1>
        <p className="mt-3 max-w-2xl text-lg text-slate-600">
          Tell us what happened. We&apos;ll take you to the right place — emergencies are
          handled immediately and are never blocked by sign-in.
        </p>
      </section>

      <ReportIncidentTriage />

      <section className="mt-8 rounded-xl border border-slate-200 bg-white p-6">
        <h2 className="font-semibold text-slate-900">What happens to your report</h2>
        <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-slate-600">
          <li>Emergency requests are triaged and dispatched straight away.</li>
          <li>
            Safety concerns and complaints open a tracked case with a reference number, and
            you can follow the outcome.
          </li>
          <li>
            You can choose to submit feedback anonymously after signing in. Fully anonymous
            reporting without an account is coming soon.
          </li>
        </ul>
      </section>
    </PublicShell>
  );
}
