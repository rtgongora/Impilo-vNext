import { PageShell } from "@/components/PageShell";
import { AuthLayout } from "@/components/AuthLayout";

export default function Page() {
  return (
    <AuthLayout>
      <PageShell title="Biometric Verification" emptyStateLabel="Place your finger on the scanner" />
    </AuthLayout>
  );
}
