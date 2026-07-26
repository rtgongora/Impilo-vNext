"use client";

import { useParams } from "next/navigation";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { PaediatricWorkspaceShell } from "@/features/paediatrics/workspace/PaediatricWorkspaceShell";

export default function PaediatricWorkspacePage() {
  const params = useParams();
  const patientId = typeof params?.patientId === "string" ? params.patientId : "";

  return (
    <EHRLayout>
      <PageShell
        title="Paediatric workspace"
        subtitle="Age-aware assessment, what is due today, and the child's growth and immunisation record."
      >
        {patientId ? (
          <PaediatricWorkspaceShell patientId={patientId} />
        ) : (
          <p className="text-sm text-muted-foreground">No patient selected.</p>
        )}
      </PageShell>
    </EHRLayout>
  );
}
