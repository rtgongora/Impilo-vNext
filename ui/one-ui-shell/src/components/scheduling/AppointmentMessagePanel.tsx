"use client";

import { useState } from "react";
import { Loader2, MessageSquare, Send } from "lucide-react";
import {
  useAppointmentMessages,
  useSendAppointmentMessage,
} from "@/hooks/queries/useAppointments";

interface AppointmentMessagePanelProps {
  appointmentId: string;
  patientLabel?: string;
}

export function AppointmentMessagePanel({ appointmentId, patientLabel }: AppointmentMessagePanelProps) {
  const [draft, setDraft] = useState("");
  const { data: messages, isLoading } = useAppointmentMessages(appointmentId);
  const sendMessage = useSendAppointmentMessage();

  const rows = Array.isArray(messages) ? messages : [];

  return (
    <div className="mt-3 rounded-lg border border-border bg-background p-3">
      <div className="mb-2 flex items-center gap-2 text-xs font-medium text-foreground">
        <MessageSquare className="h-3.5 w-3.5" />
        Appointment messages
        {patientLabel ? <span className="text-muted-foreground">· {patientLabel}</span> : null}
      </div>

      {isLoading ? (
        <div className="flex items-center gap-2 py-2 text-xs text-muted-foreground">
          <Loader2 className="h-3 w-3 animate-spin" />
          Loading thread…
        </div>
      ) : rows.length === 0 ? (
        <p className="py-1 text-xs text-muted-foreground">No messages yet. Send a note to the citizen.</p>
      ) : (
        <ul className="mb-2 max-h-40 space-y-1.5 overflow-y-auto">
          {rows.map((row) => {
            const item = row as unknown as Record<string, unknown>;
            const direction = String(item.direction ?? "unknown");
            const isProvider = direction === "provider_to_citizen";
            return (
              <li
                key={String(item.id ?? `${direction}-${item.sentAt}`)}
                className={`rounded px-2 py-1.5 text-xs ${
                  isProvider ? "bg-primary-soft text-impilo-800" : "bg-card text-foreground"
                }`}
              >
                <span className="font-medium">{isProvider ? "You" : "Citizen"}:</span>{" "}
                {String(item.message ?? "")}
              </li>
            );
          })}
        </ul>
      )}

      <div className="flex gap-2">
        <input
          type="text"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="Message the citizen about this appointment…"
          className="flex-1 rounded-lg border border-border px-3 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-primary/40"
        />
        <button
          type="button"
          disabled={!draft.trim() || sendMessage.isPending}
          onClick={() => {
            sendMessage.mutate(
              { appointmentId, message: draft.trim(), direction: "provider_to_citizen" },
              { onSuccess: () => setDraft("") },
            );
          }}
          className="inline-flex items-center gap-1 rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-white hover:bg-primary-hover disabled:opacity-50"
        >
          {sendMessage.isPending ? <Loader2 className="h-3 w-3 animate-spin" /> : <Send className="h-3 w-3" />}
          Send
        </button>
      </div>
    </div>
  );
}
