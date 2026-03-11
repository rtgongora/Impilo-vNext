import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Federation" emptyStateLabel="No federation connections" />
    </AppLayout>
  );
}
