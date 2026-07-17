import Link from "next/link";
import { PublicShell } from "@/components/public/PublicShell";
import { PublicFeedbackStatusLookup } from "@/components/public/PublicFeedbackStatusLookup";
import { DidThisWork } from "@/components/feedback/DidThisWork";

export const metadata = {
  title: "Check a report — Impilo",
  description: "Check the status of an anonymous report using your claim code.",
};

/** Claim-code status lookup for anonymous reports (gateway ADR W4). */
export default function ReportStatusPage() {
  return (
    <PublicShell>
      <nav className="text-sm text-slate-500">
        <Link href="/welcome" className="hover:text-slate-900">Welcome</Link> /{" "}
        <Link href="/welcome/report" className="hover:text-slate-900">Report a health incident</Link>{" "}
        / Check a report
      </nav>

      <section className="mt-3 rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="text-2xl font-bold text-slate-900">Check your report</h1>
        <p className="mt-2 max-w-2xl text-slate-600">
          Enter the claim code you were given when you submitted your report.
        </p>
        <div className="mt-6 max-w-xl">
          <PublicFeedbackStatusLookup />
        </div>
      </section>

      <section className="mt-6 max-w-xl">
        <DidThisWork surfaceLabel="Checking a report" />
      </section>
    </PublicShell>
  );
}
