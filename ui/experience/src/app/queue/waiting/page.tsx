import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Waiting Room" emptyStateLabel="No patients in waiting room" />
    </AppLayout>
  );
}
