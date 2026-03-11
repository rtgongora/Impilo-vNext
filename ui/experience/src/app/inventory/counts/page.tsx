import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Stock Counts" emptyStateLabel="No stock counts" />
    </AppLayout>
  );
}
