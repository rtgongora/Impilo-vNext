import { PageShell } from "@/components/PageShell";
import { AuthLayout } from "@/components/AuthLayout";

export default function Page() {
  return (
    <AuthLayout>
      <PageShell title="Forgot Password" emptyStateLabel="Enter your email to reset your password" />
    </AuthLayout>
  );
}
