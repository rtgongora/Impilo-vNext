import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Triage Queue" emptyStateLabel="No patients awaiting triage" />
    </AppLayout>
  );
}
