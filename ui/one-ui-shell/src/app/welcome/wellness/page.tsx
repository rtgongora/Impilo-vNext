import { PublicShell } from "@/components/public/PublicShell";
import { PublicWellnessExplorer } from "@/components/public/PublicWellnessExplorer";

export const metadata = {
  title: "Wellness — Impilo",
  description:
    "Screening and prevention schedules and one-off health checks (BMI, blood pressure). Read and check freely — sign in only to track progress, set goals or connect a device.",
};

/**
 * Public wellness (PF-W5 / SIMBA). Education, screening schedules and one-off compute-only
 * calculators WITHOUT sign-in. Tracking/diaries/goals/devices require sign-in (doctrine §13.3).
 */
export default function WellnessPage() {
  return (
    <PublicShell>
      <h1 className="text-3xl font-bold text-slate-900">Wellness</h1>
      <p className="mt-3 max-w-2xl text-slate-600">
        Look after your health — see recommended screening and prevention schedules and run a
        one-off health check. Everything here is free to use with no account; you only sign in
        when you want to track and save your progress.
      </p>
      <div className="mt-8">
        <PublicWellnessExplorer />
      </div>
    </PublicShell>
  );
}
