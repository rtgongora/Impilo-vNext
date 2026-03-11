import { PageShell } from "@/components/PageShell";
import { EHRLayout } from "@/components/EHRLayout";

export default function Page() {
  return (
    <EHRLayout>
      <PageShell title="Vitals" emptyStateLabel="No vitals recorded" />
    </EHRLayout>
  );
}
