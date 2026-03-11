import { PageShell } from "@/components/PageShell";
import { AppLayout } from "@/components/AppLayout";

export default function Page() {
  return (
    <AppLayout>
      <PageShell title="Select Workspace" emptyStateLabel="Choose a workspace to continue" />
    </AppLayout>
  );
}
