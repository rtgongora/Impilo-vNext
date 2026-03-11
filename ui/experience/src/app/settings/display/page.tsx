import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Display Settings" emptyStateLabel="Display preferences" />
    </AppLayout>
  );
}
