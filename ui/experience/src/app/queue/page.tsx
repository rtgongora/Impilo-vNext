import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Patient Queue" emptyStateLabel="No patients in queue" />
    </AppLayout>
  );
}
