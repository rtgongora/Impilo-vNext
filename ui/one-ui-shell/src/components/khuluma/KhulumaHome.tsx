"use client";

import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { KhulumaHomeBody } from "./KhulumaHomeBody";

/**
 * Full-page Khuluma route (/khuluma). The actual content lives in KhulumaHomeBody (Phase F12) —
 * split out so the panel can be embedded elsewhere without a second AppLayout/PageShell.
 */
export function KhulumaHome() {
  return (
    <AppLayout>
      <PageShell title="Khuluma" subtitle="Messages, calls, meetings, updates and coordination across Impilo" serviceSlug="khuluma">
        <KhulumaHomeBody />
      </PageShell>
    </AppLayout>
  );
}
