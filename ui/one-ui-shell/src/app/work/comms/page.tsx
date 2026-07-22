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
        title="Khuluma — Comms Hub"
        subtitle="Conversations, presence, calls and meetings for care coordination"
        icon={<MessageSquare className="h-5 w-5" />}
      >
        <a href="/khuluma" className="mb-3 inline-block rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm font-medium text-emerald-800 hover:bg-emerald-100">
          Open the full Khuluma hub — calls, meetings, updates, channels and escalations →
        </a>
        <CommsHub persona="work" />
      </PageShell>
    </AppLayout>
  );
}
