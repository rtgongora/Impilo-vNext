import Link from "next/link";
import { PublicShell } from "@/components/public/PublicShell";
import { ContributionStatusLookup } from "@/components/get-involved/ContributionStatusLookup";
import { DidThisWork } from "@/components/feedback/DidThisWork";

export const metadata = {
  title: "Track your idea — Impilo",
  description: "Check what happened to an idea you shared, using your one-time claim code.",
};

/** Claim-code status tracking for anonymous contributions (gateway ADR W4). */
export default function TrackContributionPage() {
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
        / Track your idea
      </nav>

      <section className="mt-3 rounded-2xl border border-white/60 bg-white/70 backdrop-blur-sm [.low-blur_&]:bg-white p-8 shadow-sm">
        <h1 className="text-2xl font-bold text-slate-900">Track your idea</h1>
        <p className="mt-2 max-w-2xl text-slate-600">
          Enter the claim code you were given when you shared your idea. It is the only way to check
          what happens next.
        </p>
        <div className="mt-6 max-w-xl">
          <ContributionStatusLookup />
        </div>
      </section>

      <section className="mt-6 max-w-xl">
        <DidThisWork surfaceLabel="Tracking an idea" />
      </section>
    </PublicShell>
  );
}
