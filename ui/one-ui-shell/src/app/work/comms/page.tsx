"use client";

import { MessageSquare } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { CommsHub } from "@/components/comms/CommsHub";

/**
 * Provider Comms Hub — the unified work-context conversation/call surface (Khuluma). Care
 * coordination threads, presence, and 1:1 calls under Tshepo-governed trust.
 */
export default function WorkCommsPage() {
  return (
    <AppLayout>
      <PageShell
        title="Work Comms Hub"
        subtitle="Conversations, presence and calls for care coordination"
        icon={<MessageSquare className="h-5 w-5" />}
      >
        <CommsHub persona="work" />
      </PageShell>
    </AppLayout>
  );
}
