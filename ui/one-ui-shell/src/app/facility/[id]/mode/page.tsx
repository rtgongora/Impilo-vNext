"use client";

/**
 * Facility Mode (IATG Journey E). Route: /facility/[id]/mode — the facility-wide
 * operational/admin context a provider switches into when they administer this
 * facility. Admin actions are gated per facility inside the dashboard.
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FacilityModeDashboard } from "@/components/facility/FacilityModeDashboard";

export default function FacilityModePage() {
  const params = useParams();
  const id = params.id as string;

  return (
    <AppLayout>
      <PageShell title="Facility Mode" subtitle="Facility-wide operational and administrative context.">
        <div className="mb-4">
          <Link
            href={`/facility/${id}`}
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Back to facility
          </Link>
        </div>
        <FacilityModeDashboard facilityId={id} />
      </PageShell>
    </AppLayout>
  );
}
