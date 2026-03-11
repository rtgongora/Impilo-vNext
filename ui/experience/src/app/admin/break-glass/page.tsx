import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Break Glass Log" emptyStateLabel="No break glass events" />
    </AppLayout>
  );
}
