import { PageShell } from "@/components/PageShell";
import { EHRLayout } from "@/components/EHRLayout";

export default function Page() {
  return (
    <EHRLayout>
      <PageShell title="Encounters" emptyStateLabel="No encounters recorded" />
    </EHRLayout>
  );
}
