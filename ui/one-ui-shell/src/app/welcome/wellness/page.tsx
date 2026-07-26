import { PublicShell } from "@/components/public/PublicShell";
import { PublicPageHeader } from "@/components/public/PublicPrimitives";
import { PublicWellnessExplorer } from "@/components/public/PublicWellnessExplorer";
import { ReasonedSignInPrompt } from "@/components/public/ReasonedSignInPrompt";

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
      <PublicPageHeader
        crumbs={[{ label: "Impilo", href: "/" }, { label: "Wellness" }]}
        title="Wellness"
        lede={
          <>
            Look after your health — see recommended screening and prevention schedules and run a one-off health check. Everything here is free to use with no account; you only sign in when you want to track and save your progress.
          </>
        }
      />
      <div className="mt-8">
        <PublicWellnessExplorer />
      </div>
      <ReasonedSignInPrompt
        pillar="my-health"
        goal="save-wellness-progress"
        destination="/wellness"
        publicReturn="/welcome/wellness"
        reason="Sign in only when you want Impilo to save your checks, track progress, set goals, connect a device or share with a care team."
      />
    </PublicShell>
  );
}
