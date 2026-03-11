import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Tenant Management" emptyStateLabel="No tenants configured" />
    </AppLayout>
  );
}
