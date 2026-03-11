import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Tariff Management" emptyStateLabel="No tariffs configured" />
    </AppLayout>
  );
}
