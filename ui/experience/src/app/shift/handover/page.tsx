import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Shift Handover" emptyStateLabel="Hand over your shift" />
    </AppLayout>
  );
}
