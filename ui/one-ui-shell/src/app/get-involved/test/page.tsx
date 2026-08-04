import Link from "next/link";
import { PublicShell } from "@/components/public/PublicShell";
import { TestingCohorts } from "@/components/get-involved/TestingCohorts";

export const metadata = {
  title: "Help test Impilo",
  description:
    "Join a testing group and help shape Impilo features before they go live. No account needed to sign up.",
};

/** Open testing / participation cohorts (gateway ADR W4). Reachable WITHOUT login. */
export default function HelpTestPage() {
  return (
    <PublicShell>
      <nav className="text-sm text-slate-500">
        <Link href="/welcome" className="hover:text-slate-900">
          Welcome
        </Link>{" "}
        /{" "}
        <Link href="/get-involved" className="hover:text-slate-900">
          Get involved
        </Link>{" "}
        / Help test
      </nav>

      <section className="mt-3 rounded-2xl border border-white/60 bg-white/70 backdrop-blur-sm [.low-blur_&]:bg-white p-8 shadow-sm">
        <h1 className="text-2xl font-bold text-slate-900">Help test Impilo &amp; join in</h1>
        <p className="mt-2 max-w-2xl text-slate-600">
          Join a testing group to try new features early and tell us what works. We&apos;ll only use
          your contact to reach you about testing.
        </p>
      </section>

      <section className="mt-6">
        <TestingCohorts />
      </section>
    </PublicShell>
  );
}
