"use client";

/**
 * Facility Mode setup wizard.
 * Route: /facility/[id]/setup
 *
 * T4 owns this route. Drives the TUSO setup-state SoR through
 * dept -> service-point -> queue -> workflow -> workforce -> OROS-routing ->
 * Khuluma-channel -> Fundo-readiness -> go-live. No fake completions.
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { SetupWizard } from "@/components/facility-mode/SetupWizard";

export default function FacilitySetupPage() {
  const params = useParams();
  const id = params.id as string;

  return (
    <AppLayout>
      <PageShell title="Facility setup" subtitle="Configure this facility for go-live">
        <div className="mb-4">
          <Link
            href={`/facility/${id}/cockpit`}
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Back to cockpit
          </Link>
        </div>
        <div className="max-w-3xl">
          <SetupWizard facilityId={id} />
        </div>
      </PageShell>
    </AppLayout>
  );
}
