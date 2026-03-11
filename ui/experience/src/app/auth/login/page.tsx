import { PageShell } from "@/components/PageShell";
import { AuthLayout } from "@/components/AuthLayout";

export default function Page() {
  return (
    <AuthLayout>
      <PageShell title="Sign In" emptyStateLabel="Enter your credentials to sign in" />
    </AuthLayout>
  );
}
