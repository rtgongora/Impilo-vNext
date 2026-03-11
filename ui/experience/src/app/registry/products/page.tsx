import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Product Registry" emptyStateLabel="No products found" />
    </AppLayout>
  );
}
