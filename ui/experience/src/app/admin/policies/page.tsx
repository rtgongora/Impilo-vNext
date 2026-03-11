import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Policy Management" emptyStateLabel="No policies defined" />
    </AppLayout>
  );
}
