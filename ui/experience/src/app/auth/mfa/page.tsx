import { PageShell } from "@/components/PageShell";
import { AuthLayout } from "@/components/AuthLayout";

export default function Page() {
  return (
    <AuthLayout>
      <PageShell title="Multi-Factor Authentication" emptyStateLabel="Enter your verification code" />
    </AuthLayout>
  );
}
