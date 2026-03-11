import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Order Details" emptyStateLabel="Order information" />
    </AppLayout>
  );
}
