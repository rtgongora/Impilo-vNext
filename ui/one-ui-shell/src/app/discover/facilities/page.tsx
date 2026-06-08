"use client";

import { Building } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { DiscoverFacilitiesMapPanel } from "@/components/maps/DiscoverFacilitiesMapPanel";

export default function DiscoverFacilitiesPage() {
  return (
    <AppLayout>
      <PageShell
        title="Find a Facility"
        subtitle="Locate clinics, hospitals, and health service points on governed Ndila maps"
        icon={<Building className="h-6 w-6" />}
      >
        <DiscoverFacilitiesMapPanel />
      </PageShell>
    </AppLayout>
  );
}
