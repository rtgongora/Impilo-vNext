import { AuthLayout } from "@/components/AuthLayout";
import { ProgressiveAuthForm } from "@/components/auth/ProgressiveAuthForm";
import { NompiloAdaptiveGuidance } from "@/components/intelligent/NompiloAdaptiveGuidance";

interface LoginPageProps {
  searchParams?: { returnTo?: string; goal?: string };
}

export default function LoginPage({ searchParams }: LoginPageProps) {
  const returnTo = searchParams?.returnTo || null;

  const guidanceMessage = returnTo?.includes("/welcome/find-care")
    ? "Sign in to confirm your booking and save it directly to your health record. Your facility and appointment selections are preserved."
    : returnTo?.includes("/work") || returnTo?.includes("/provider")
    ? "Sign in to practice as a verified health provider. Select 'Work & Practice' to land directly in your clinical workplace."
    : "Sign in to Impilo to access your health records, care scheduling, or professional services safely.";

  return (
    <AuthLayout returnTo={returnTo}>
      <ProgressiveAuthForm returnTo={returnTo} />
      <NompiloAdaptiveGuidance
        contextMessage={guidanceMessage}
        suggestions={[
          "Forgot your password? Use the link inside the form",
          "New here? Tap 'Create an account' to get started",
        ]}
      />
    </AuthLayout>
  );
}
