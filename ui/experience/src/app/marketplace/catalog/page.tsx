import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Service Catalog" emptyStateLabel="No services available" />
    </AppLayout>
  );
}
