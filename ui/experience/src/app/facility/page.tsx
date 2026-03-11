import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Select Facility" emptyStateLabel="Choose a facility to continue" />
    </AppLayout>
  );
}
