import { PageShell } from "@/components/PageShell";
import { AuthLayout } from "@/components/AuthLayout";

export default function Page() {
  return (
    <AuthLayout>
      <PageShell title="Sign In with Email" emptyStateLabel="Enter your email and password" />
    </AuthLayout>
  );
}
