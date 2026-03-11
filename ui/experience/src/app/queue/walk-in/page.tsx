import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Walk-in Registration" emptyStateLabel="Register a walk-in patient" />
    </AppLayout>
  );
}
