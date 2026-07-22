"use client";

import type { ReactNode } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { KhulumaSubNav } from "./KhulumaSubNav";

/** Shared chrome for every /khuluma/* workspace: branded hero + the Khuluma sub-nav. */
export function KhulumaWorkspace({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <AppLayout>
      <PageShell title={title} subtitle={subtitle} serviceSlug="khuluma">
        <KhulumaSubNav />
        <div className="space-y-4">{children}</div>
      </PageShell>
    </AppLayout>
  );
}
