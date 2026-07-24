import { PublicShell } from "@/components/public/PublicShell";
import { PublicLearningCatalog } from "@/components/public/PublicLearningCatalog";
import { ReasonedSignInPrompt } from "@/components/public/ReasonedSignInPrompt";

export const metadata = {
  title: "Learning — Impilo",
  description:
    "Free citizen health courses, caregiver education, first aid and digital-health literacy. Read freely — sign in only for certificates, CPD or progress tracking.",
};

/**
 * Public learning catalog (PF-W5 / FUNDO). Browse the PUBLISHED course library WITHOUT
 * sign-in. Enrolment, assessed learning, certificates and CPD require sign-in.
 */
export default function LearningPage() {
  return (
    <PublicShell>
      <h1 className="text-3xl font-bold text-slate-900">Learning</h1>
      <p className="mt-3 max-w-2xl text-slate-600">
        Learn about your health — citizen courses, caregiver education, first aid and digital-
        health skills. Read freely; sign in only when you want certificates, CPD credits or to
        track your progress.
      </p>
      <div className="mt-8">
        <PublicLearningCatalog />
      </div>
      <ReasonedSignInPrompt
        pillar="health-information"
        goal="track-learning"
        destination="/learning"
        publicReturn="/welcome/learning"
        reason="Sign in only when you want to enrol, track progress, complete assessed learning, earn a certificate or record CPD."
      />
    </PublicShell>
  );
}
