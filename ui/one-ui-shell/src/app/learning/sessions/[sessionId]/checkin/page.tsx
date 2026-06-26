"use client";

import { useParams } from "next/navigation";
import { useState } from "react";
import { PageShell } from "@/components/PageShell";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";
import { useSelfCheckin, useSessionAttendance } from "@/hooks/queries/useFundoDelivery";

export default function SessionCheckinPage() {
  const params = useParams();
  const sessionId = String(params?.sessionId ?? "");
  const subject = useLearningSubject();
  const checkin = useSelfCheckin(sessionId);
  const { data } = useSessionAttendance(sessionId);
  const [token, setToken] = useState("");
  const [message, setMessage] = useState<string | null>(null);

  const attendees = (((data?.data as { items?: Array<{ id: string; subjectId: string; status?: string; checkInMethod?: string }> } | undefined)?.items) ?? []);

  async function onCheckin() {
    setMessage(null);
    try {
      await checkin.mutateAsync({
        token,
        subjectType: subject.subjectType,
        subjectId: subject.subjectId,
        method: "CODE",
      });
      setMessage("Checked in.");
      setToken("");
    } catch {
      setMessage("Check-in failed — token may be invalid or expired.");
    }
  }

  return (
    <PageShell title="Session check-in" subtitle="Enter the code shown by your facilitator to mark your attendance.">
      <div className="rounded-lg border border-border bg-card p-4">
        <div className="flex gap-2">
          <input
            value={token}
            onChange={(e) => setToken(e.target.value)}
            placeholder="6-digit code"
            inputMode="numeric"
            className="rounded border border-border px-3 py-2 text-sm"
          />
          <button
            onClick={onCheckin}
            disabled={!token.trim() || checkin.isPending}
            className="rounded bg-teal-600 px-4 py-2 text-sm text-white disabled:opacity-60"
          >
            Check in
          </button>
        </div>
        {message && <p className="mt-2 text-sm text-muted-foreground" data-testid="checkin-message">{message}</p>}
      </div>
      <div className="mt-4 rounded-lg border border-border bg-card p-4">
        <p className="text-sm font-semibold text-foreground">Attendance ({attendees.length})</p>
        <ul className="mt-2 space-y-1 text-sm">
          {attendees.map((a) => (
            <li key={a.id} className="rounded border border-border px-3 py-1.5">
              {a.subjectId} · {a.status ?? "PRESENT"}{a.checkInMethod ? ` · ${a.checkInMethod}` : ""}
            </li>
          ))}
        </ul>
      </div>
    </PageShell>
  );
}
