import { PageShell } from "@/components/PageShell";
import { AuthLayout } from "@/components/AuthLayout";

export default function Page() {
  return (
    <AuthLayout>
      <PageShell title="Reset Password" emptyStateLabel="Enter your new password" />
    </AuthLayout>
  );
}
