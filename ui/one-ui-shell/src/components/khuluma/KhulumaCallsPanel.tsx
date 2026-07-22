"use client";

import { useState } from "react";
import Link from "next/link";
import { Phone, PhoneIncoming, Loader2 } from "lucide-react";
import { useIncomingCalls, useCallActions, type CallResponse } from "@/hooks/queries/useComms";
import { CommsCallModal } from "@/components/comms/CommsCallModal";
import { KhulumaSection } from "./KhulumaSection";
import { KhulumaEmptyState } from "./KhulumaEmptyState";
import { rows, asRecord, readStr } from "./util";

/**
 * Khuluma Calls workspace — surfaces the real call lifecycle (incoming ring → accept/decline
 * → media room via the shared CommsCallModal). Call history has no khuluma-service endpoint,
 * so that is an honest zero-state, never an invented list. Calls are placed from a conversation.
 */
export function KhulumaCallsPanel() {
  const incomingQ = useIncomingCalls(true);
  const actions = useCallActions();
  const [activeCall, setActiveCall] = useState<CallResponse | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const incoming = rows(incomingQ.data);

  async function accept(callId: string) {
    setBusy(callId);
    try {
      const res = await actions.accept(callId);
      setActiveCall(asRecord(res) as unknown as CallResponse);
    } finally {
      setBusy(null);
      void incomingQ.refetch();
    }
  }
  async function decline(callId: string) {
    setBusy(callId);
    try {
      await actions.decline(callId);
    } finally {
      setBusy(null);
      void incomingQ.refetch();
    }
  }

  return (
    <>
      <KhulumaSection
        title="Incoming calls"
        icon={<PhoneIncoming className="h-4 w-4 text-emerald-700" />}
        isLoading={incomingQ.isLoading}
        isError={incomingQ.isError}
        onRetry={() => incomingQ.refetch()}
        isEmpty={!incoming.length}
        emptyState={
          <KhulumaEmptyState
            icon={<Phone className="h-6 w-6" />}
            title="No calls waiting"
            explanation="Audio and video calls placed to you ring here. Call history is not yet recorded by the platform, so only live incoming calls are shown."
            when="A call appears here the moment someone calls you."
            actions={[{ label: "Start a call from a conversation", href: "/khuluma/inbox" }]}
          />
        }
      >
        <ul className="divide-y divide-border">
          {incoming.map((c, i) => {
            const id = readStr(c, "callId", "id");
            return (
              <li key={id || i} className="flex items-center justify-between gap-2 py-2 text-sm">
                <span className="flex items-center gap-2 text-foreground">
                  <PhoneIncoming className="h-4 w-4 text-emerald-700" />
                  {readStr(c, "callerName", "callerId") || "Incoming call"}
                  <span className="text-xs text-muted-foreground">({readStr(c, "callType") || "AUDIO"})</span>
                </span>
                <span className="flex gap-2">
                  <button type="button" disabled={busy === id} onClick={() => accept(id)} className="rounded-md bg-emerald-600 px-2.5 py-1 text-xs font-medium text-white hover:bg-emerald-700 disabled:opacity-50">
                    {busy === id ? <Loader2 className="h-3 w-3 animate-spin" /> : "Accept"}
                  </button>
                  <button type="button" disabled={busy === id} onClick={() => decline(id)} className="rounded-md border border-red-200 px-2.5 py-1 text-xs font-medium text-danger hover:bg-red-50 disabled:opacity-50">
                    Decline
                  </button>
                </span>
              </li>
            );
          })}
        </ul>
      </KhulumaSection>

      <p className="text-xs text-muted-foreground">
        To place a call, open a conversation in your <Link href="/khuluma/inbox" className="font-medium text-emerald-700 hover:text-emerald-900">Inbox</Link> and use its call action.
      </p>

      {activeCall ? <CommsCallModal call={activeCall} onEnd={() => setActiveCall(null)} /> : null}
    </>
  );
}
