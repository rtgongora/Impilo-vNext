"use client";

/**
 * Standalone resuscitation workspace — direct entry to the ABCDE resus surface for a
 * known emergency activation (e.g. deep-linked from a code-blue page / handover).
 * Route: /clinical/emergency/resus/{activationId}?episode=&patient=&trauma=&mechanism=
 */

import Link from "next/link";
import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { ResuscitationWorkspace } from "@/components/clinical/ResuscitationWorkspace";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";

function ResusInner({ activationId }: { activationId: string }) {
  const params = useSearchParams();
  return (
    <>
      <div className="mb-4 flex justify-end">
        <Link href="/clinical/emergency" className="text-sm text-primary underline">
          ← ED trackboard
        </Link>
      </div>
      <ResuscitationWorkspace
        activationId={activationId}
        traumaEpisodeId={params.get("episode") ?? undefined}
        patientLabel={params.get("patient") ?? undefined}
        traumaId={params.get("trauma") ?? undefined}
        mechanism={params.get("mechanism") ?? undefined}
        location={params.get("location") ?? undefined}
      />
      <div className="mt-6">
        <NompiloContextualGuidance routePath="/clinical/emergency/resus" />
      </div>
    </>
  );
}

export default function ResusPage({ params }: { params: { activationId: string } }) {
  return (
    <AppLayout>
      <PageShell title="Resuscitation" subtitle="ABCDE resuscitation workspace" serviceSlug="daidzai">
        <Suspense fallback={<div className="py-16 text-sm text-muted-foreground">Loading…</div>}>
          <ResusInner activationId={params.activationId} />
        </Suspense>
      </PageShell>
    </AppLayout>
  );
}
