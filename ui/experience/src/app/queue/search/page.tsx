import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Patient Search" emptyStateLabel="Search for a patient" />
    </AppLayout>
  );
}
