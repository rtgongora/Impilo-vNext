import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Billing" emptyStateLabel="No billing records" />
    </AppLayout>
  );
}
