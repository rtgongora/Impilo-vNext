import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Facility Reports" emptyStateLabel="No facility reports" />
    </AppLayout>
  );
}
