import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Active Shift" emptyStateLabel="Current shift details" />
    </AppLayout>
  );
}
