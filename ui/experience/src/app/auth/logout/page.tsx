import { PageShell } from "@/components/PageShell";
import { AuthLayout } from "@/components/AuthLayout";

export default function Page() {
  return (
    <AuthLayout>
      <PageShell title="Signing Out" emptyStateLabel="You are being signed out..." />
    </AuthLayout>
  );
}
