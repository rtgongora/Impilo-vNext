import { PublicShell } from "@/components/public/PublicShell";
import { PublicCoverageCompare } from "@/components/public/PublicCoverageCompare";

export const metadata = {
  title: "Health cover — Impilo",
  description:
    "Compare medical-aid and insurance plans and their benefits. Read freely — sign in only to view your membership, check eligibility or make a claim.",
};

/**
 * Public coverage compare (PF-W4 / RUVIMBO). Plan/benefit catalogue comparison WITHOUT
 * sign-in. Membership, eligibility, authorisations and claims require sign-in.
 */
export default function CoveragePage() {
  return (
    <PublicShell>
      <h1 className="text-3xl font-bold text-slate-900">Health cover</h1>
      <p className="mt-3 max-w-2xl text-slate-600">
        Compare medical-aid and insurance plans and what they cover. Browse freely; you only
        need to sign in to view your own membership, check eligibility or make a claim.
      </p>
      <div className="mt-8">
        <PublicCoverageCompare />
      </div>
    </PublicShell>
  );
}
