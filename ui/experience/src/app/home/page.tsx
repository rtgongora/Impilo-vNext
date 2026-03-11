import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Home" emptyStateLabel="Welcome to Impilo vNext" />
    </AppLayout>
  );
}
