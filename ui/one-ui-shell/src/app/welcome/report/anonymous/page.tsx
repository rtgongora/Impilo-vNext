import Link from "next/link";
import { PublicShell } from "@/components/public/PublicShell";
import { PublicFeedbackForm } from "@/components/public/PublicFeedbackForm";

export const metadata = {
  title: "Report anonymously — Impilo",
  description:
    "Report unsafe care, a safety concern or a complaint without an account. You receive a claim code to check the outcome.",
};

/** Anonymous public report intake (gateway ADR W4 gateway-feedback-claim). */
export default function AnonymousReportPage() {
  return (
    <PublicShell>
      <nav className="text-sm text-slate-500">
        <Link href="/welcome" className="hover:text-slate-900">Welcome</Link> /{" "}
        <Link href="/welcome/report" className="hover:text-slate-900">Report a health incident</Link>{" "}
        / Anonymous report
      </nav>

      <section className="mt-3 rounded-2xl border border-slate-200 bg-white p-8 shadow-sm">
        <h1 className="text-2xl font-bold text-slate-900">Report without an account</h1>
        <p className="mt-2 max-w-2xl text-slate-600">
          Your identity is not recorded. When you submit, you get a one-time claim code —
          keep it safe, it is the only way to check what happened with your report.
        </p>
        <div className="mt-6 max-w-xl">
          <PublicFeedbackForm />
        </div>
      </section>
    </PublicShell>
  );
}
