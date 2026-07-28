"use client";

/**
 * Chronic-disease registers (brief.md §8).
 * Route: /clinical/chronic-registers | pageTitle: "Chronic disease registers"
 *
 * Facility-scoped rather than patient-scoped — this is the estate's first cohort surface. Thin by
 * design: the shell lives in features/medicine/registers so it can be tested without the chart.
 */

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { ChronicRegisterShell } from "@/features/medicine/registers/ChronicRegisterShell";

export default function ChronicRegistersPage() {
  return (
    <AppLayout>
      <PageShell
        title="Chronic disease registers"
        subtitle="Who is on each register at this facility, and who needs recalling."
      >
        <div className="space-y-4">
          <div className="rounded-lg border border-border bg-muted/30 p-4 text-sm text-muted-foreground">
            People nobody has assessed are listed first — they are who a recall list exists to find.
            An empty control column would read as &quot;fine&quot;, so this list says{" "}
            <strong>not assessed</strong> instead.
          </div>
          <ChronicRegisterShell />
        </div>
      </PageShell>
    </AppLayout>
  );
}
