"use client";

/**
 * My telehealth visit — the citizen/caregiver side of a teleconsult.
 *
 * State machine:
 *   WAITING_ROOM  — before a media token is held (device check → ask to join →
 *                   poll while the care team decides)
 *   IN_CONSULT    — token held; the visit fills the page
 *   POST_CONSULT  — the session reached a terminal state (responded/closed)
 *
 * Plain language throughout — no transport/vendor jargon on this surface.
 */

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Loader2, Video } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { PatientWaitingRoom, type PatientMediaGrant } from "@/components/telemedicine/patient/PatientWaitingRoom";
import { PatientConsultView } from "@/components/telemedicine/patient/PatientConsultView";
import { ConsentWithdrawControl } from "@/components/telemedicine/patient/ConsentWithdrawControl";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import { PostConsultSummary } from "@/components/telemedicine/patient/PostConsultSummary";
import { apiClient } from "@/lib/api-client";
import { useAuthStore } from "@/hooks/useAuthStore";

/** Session statuses after which the consult is over for the person. */
const TERMINAL_STATUSES = new Set(["RESPONDED", "CLOSED", "COMPLETED", "CANCELLED", "ENDED"]);

type VisitStage = "WAITING_ROOM" | "IN_CONSULT" | "POST_CONSULT";

export default function MyTelehealthVisitPage() {
  const params = useParams();
  const sessionId = params.sessionId as string;
  const user = useAuthStore((s) => s.user);

  const [session, setSession] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(true);
  const [grant, setGrant] = useState<PatientMediaGrant | null>(null);
  const [leftVisit, setLeftVisit] = useState(false);
  const [consentWithdrawn, setConsentWithdrawn] = useState(false);

  const loadSession = useCallback(async () => {
    try {
      const res = await apiClient.get<{ data: Record<string, unknown> }>(
        `/internal/v1/teleconsult/sessions/${sessionId}`,
      );
      setSession(res.data);
      return res.data;
    } catch {
      setSession(null);
      return null;
    } finally {
      setLoading(false);
    }
  }, [sessionId]);

  useEffect(() => {
    void loadSession();
  }, [loadSession]);

  const status = String(session?.status ?? "").toUpperCase();
  const isTerminal = TERMINAL_STATUSES.has(status);
  const stage: VisitStage = isTerminal ? "POST_CONSULT" : grant ? "IN_CONSULT" : "WAITING_ROOM";

  const attributes = ((session?.attributes as Record<string, unknown> | undefined) ?? session ?? {}) as Record<string, unknown>;
  const consentGranted = Boolean(session?.consentToken ?? attributes.consentReference);
  const specialty = typeof session?.specialty === "string" ? (session.specialty as string) : undefined;
  const displayName = user?.displayName || user?.email || "You";

  async function handleLeave() {
    // The person left (or the room closed). Re-read the session: if the care
    // team finished it we show the wrap-up; otherwise they can rejoin.
    setGrant(null);
    setLeftVisit(true);
    await loadSession();
  }

  // TM-B5 B5-4: consent withdrawn mid-session — the withdrawal is already recorded server-side
  // (WITHDRAWN + media floored to ASYNC); dropping the grant unmounts the room immediately, which
  // stops local publish within a frame. The visit continues on the secure message thread.
  async function handleConsentWithdrawn() {
    setConsentWithdrawn(true);
    setGrant(null);
    setLeftVisit(true);
    await loadSession();
  }

  if (loading) {
    return (
      <AppLayout>
        <div className="flex items-center justify-center h-[calc(100vh-8rem)]">
          <Loader2 className="w-6 h-6 animate-spin text-muted-foreground" />
          <span className="ml-2 text-sm text-muted-foreground">Getting your visit ready...</span>
        </div>
      </AppLayout>
    );
  }

  if (!session) {
    return (
      <AppLayout>
        <PageShell title="Video visit" subtitle="We couldn't find this visit">
          <div className="max-w-lg mx-auto text-center py-12 space-y-3">
            <p className="text-sm text-muted-foreground">
              This visit may have been moved or removed. Check your visits list, or contact
              your clinic if you were expecting an appointment.
            </p>
            <Link href="/my/telehealth" className="inline-flex items-center gap-1 text-sm text-primary hover:underline">
              <ArrowLeft className="w-4 h-4" /> Back to my visits
            </Link>
          </div>
        </PageShell>
      </AppLayout>
    );
  }

  // In-consult fills the page — keep chrome minimal so the video is the stage.
  if (stage === "IN_CONSULT" && grant) {
    return (
      <AppLayout>
        <div className="p-3 sm:p-4 space-y-2">
          <div className="flex items-center gap-3">
            <Link href="/my/telehealth" className="text-muted-foreground hover:text-foreground">
              <ArrowLeft className="w-4 h-4" />
            </Link>
            <Video className="w-4 h-4 text-primary" />
            <span className="text-sm font-semibold text-foreground">Your video visit</span>
            <span className="ml-auto">
              <ConsentWithdrawControl sessionId={sessionId} onWithdrawn={handleConsentWithdrawn} />
            </span>
          </div>
          <PatientConsultView
            roomUrl={grant.roomUrl}
            token={grant.token}
            startAudioOnly={String(grant.mediaProfile ?? "").toUpperCase() === "AUDIO_ONLY"}
            onLeave={handleLeave}
          />
        </div>
      </AppLayout>
    );
  }

  return (
    <AppLayout>
      <PageShell
        title={stage === "POST_CONSULT" ? "Visit summary" : "Your video visit"}
        subtitle={
          stage === "POST_CONSULT"
            ? "What your care team wants you to know"
            : "You'll be connected as soon as the care team is ready"
        }
        icon={<Video className="h-5 w-5" />}
        density="compact"
      >
        <div className="mb-3">
          <Link href="/my/telehealth" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="w-4 h-4" /> Back to my visits
          </Link>
        </div>
        {stage === "POST_CONSULT" ? (
          <PostConsultSummary session={session} />
        ) : (
          <div className="space-y-3">
            {consentWithdrawn ? (
              <div className="max-w-2xl mx-auto rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200" data-testid="consent-withdrawn-notice">
                Your consent withdrawal was recorded and video/audio has ended. Your visit is not
                cancelled — the care team will continue with you by secure message.
              </div>
            ) : leftVisit && (
              <div className="max-w-2xl mx-auto rounded-lg border border-border bg-card p-3 text-sm text-muted-foreground" data-testid="patient-left-notice">
                You left the visit. If that was a mistake, you can ask to join again below.
              </div>
            )}
            <PatientWaitingRoom
              sessionId={sessionId}
              displayName={displayName}
              consentGranted={consentGranted}
              specialty={specialty}
              onAdmitted={setGrant}
            />
            {/* TM-B10: route-bound Nompilo guidance — waiting-room status, device check, privacy,
                danger-sign escalate-only, reconnection. Guidance only; never clinical judgement. */}
            <div className="max-w-2xl mx-auto">
              <NompiloContextualGuidance routePath="/my/telehealth" />
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
