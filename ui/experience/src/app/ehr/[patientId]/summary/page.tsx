import { PageShell } from "@/components/PageShell";
import { EHRLayout } from "@/components/EHRLayout";

export default function Page() {
  return (
    <EHRLayout>
      <PageShell title="Patient Summary" emptyStateLabel="No summary data available" />
    </EHRLayout>
  );
}
