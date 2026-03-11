import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Operational Reports" emptyStateLabel="No operational reports" />
    </AppLayout>
  );
}
