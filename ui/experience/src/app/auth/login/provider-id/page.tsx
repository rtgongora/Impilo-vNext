import { PageShell } from "@/components/PageShell";
import { AuthLayout } from "@/components/AuthLayout";

export default function Page() {
  return (
    <AuthLayout>
      <PageShell title="Sign In with Provider ID" emptyStateLabel="Enter your Provider ID" />
    </AuthLayout>
  );
}
