"use client";

import { MessageSquare } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { CommsHub } from "@/components/comms/CommsHub";

/**
 * Citizen Comms Hub — the person-context messaging/call surface (Khuluma). Conversations with
 * care teams, presence, and 1:1 calls.
 */
export default function MyCommsPage() {
  return (
    <AppLayout>
      <PageShell
        title="Khuluma — Messages"
        subtitle="Your conversations, presence, calls and meetings"
        icon={<MessageSquare className="h-5 w-5" />}
      >
        <a href="/khuluma" className="mb-3 inline-block rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm font-medium text-emerald-800 hover:bg-emerald-100">
          Open the full Khuluma hub — calls, meetings, updates and more →
        </a>
        <CommsHub persona="life" />
      </PageShell>
    </AppLayout>
  );
}
