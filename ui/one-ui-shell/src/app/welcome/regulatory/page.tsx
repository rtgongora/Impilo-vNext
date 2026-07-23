import { PublicShell } from "@/components/public/PublicShell";
import { PublicRegulatoryExplorer } from "@/components/public/PublicRegulatoryExplorer";

export const metadata = {
  title: "Registration & licensing — Impilo",
  description:
    "How to register a health professional or license a facility: application types, requirements, fees, classifications, rules and professional councils. Read freely — sign in only to start an application.",
};

/**
 * Public regulatory & professional information (PF-W2 / gateway pillar: Applications &
 * licensing). Reference-only "what you need to apply" — readable WITHOUT sign-in.
 * Starting or managing an application requires sign-in.
 */
export default function RegulatoryPage() {
  return (
    <PublicShell>
      <h1 className="text-3xl font-bold text-slate-900">Registration &amp; licensing</h1>
      <p className="mt-3 max-w-2xl text-slate-600">
        What you need to register as a health professional or license a health facility —
        application routes, requirements, fees and the professional councils. Read freely; you
        only need to sign in when you start an actual application.
      </p>
      <div className="mt-8">
        <PublicRegulatoryExplorer />
      </div>
    </PublicShell>
  );
}
