"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Video, Radio, Loader2 } from "lucide-react";
import { KhulumaWorkspace } from "@/components/khuluma/KhulumaWorkspace";
import { KhulumaSection } from "@/components/khuluma/KhulumaSection";
import { KhulumaEmptyState } from "@/components/khuluma/KhulumaEmptyState";
import { useMeetingActions } from "@/hooks/queries/useComms";
import { asRecord, readStr } from "@/components/khuluma/util";

/**
 * Khuluma Meetings — start an instant meeting (a private Impilo Live room) or join by invite.
 * Scheduled meetings/webinars are owned by Impilo Live (link-out); Khuluma provisions the
 * ad-hoc room and carries the surrounding conversation.
 */
export default function KhulumaMeetingsPage() {
  const router = useRouter();
  const actions = useMeetingActions();
  const [title, setTitle] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function startInstant() {
    setBusy(true);
    setError(null);
    try {
      const res = asRecord(await actions.create({ title: title.trim() || "Instant meeting", participants: [] }));
      const conversationId = readStr(res, "conversationId");
      if (conversationId) router.push(`/meet/${conversationId}`);
      else setError("Could not start the meeting room.");
    } catch {
      setError("Could not start the meeting room.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <KhulumaWorkspace title="Khuluma — Meetings" subtitle="Instant meetings and live sessions">
      <KhulumaSection title="Start a meeting" icon={<Video className="h-4 w-4 text-emerald-700" />}>
        <div className="flex flex-wrap gap-2">
          <input
            className="min-w-[220px] flex-1 rounded-lg border border-border px-3 py-2 text-sm"
            placeholder="Meeting title (optional)"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
          <button type="button" disabled={busy} onClick={startInstant} className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50">
            {busy ? <Loader2 className="mr-1 inline h-4 w-4 animate-spin" /> : null}Start instant meeting
          </button>
        </div>
        {error ? <p className="mt-2 text-sm text-danger">{error}</p> : null}
        <p className="mt-2 text-xs text-muted-foreground">The room opens in Impilo Live with a knock lobby; invite people from inside the room.</p>
      </KhulumaSection>

      <KhulumaSection title="Scheduled sessions & webinars" icon={<Radio className="h-4 w-4 text-emerald-700" />}>
        <KhulumaEmptyState
          icon={<Radio className="h-6 w-6" />}
          title="Scheduled meetings live in Impilo Live"
          explanation="Webinars, teleconsultations, MDTs and scheduled live sessions are hosted by Impilo Live. Khuluma brings the right people together; Impilo Live gives them the room."
          when="Your scheduled and upcoming live sessions appear on the Impilo Live stage."
          actions={[{ label: "Open Impilo Live", href: "/live" }]}
        />
      </KhulumaSection>
    </KhulumaWorkspace>
  );
}
