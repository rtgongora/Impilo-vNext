"use client";

/**
 * Patient waiting room — plain-language pre-consult surface for citizens and
 * caregivers. Explains what happens next, shows consent status, runs a device
 * check (camera preview), then asks to join the visit. While the care team
 * has not yet admitted the person, the token endpoint answers WAITING and this
 * component polls every 5 seconds (with the session.admitted realtime event as
 * an accelerator — realtime is enhancement-only, polling always continues).
 */

import { useCallback, useEffect, useRef, useState } from "react";
import { CheckCircle2, Clock, HeartHandshake, Loader2, ShieldCheck, ShieldQuestion } from "lucide-react";
import { PreJoinPanel } from "@/components/session/PreJoinPanel";
import { useSessionRealtime } from "@/hooks/useSessionRealtime";
import {
  useTelemedicineMediaToken,
  type TelemedicineMediaRole,
} from "@/hooks/queries/useTelemedicine";

export interface PatientMediaGrant {
  roomUrl: string;
  token: string;
  mediaProfile?: string;
}

const POLL_INTERVAL_MS = 5_000;

type WaitingStage = "device_check" | "waiting" | "denied";

export function PatientWaitingRoom({
  sessionId,
  displayName,
  role = "PATIENT",
  consentGranted,
  specialty,
  onAdmitted,
}: {
  sessionId: string;
  displayName: string;
  /** PATIENT by default; caregivers join the same room with role CAREGIVER. */
  role?: TelemedicineMediaRole;
  consentGranted: boolean;
  specialty?: string;
  onAdmitted: (grant: PatientMediaGrant) => void;
}) {
  const [stage, setStage] = useState<WaitingStage>("device_check");
  const [deviceError, setDeviceError] = useState<string | null>(null);
  const mediaToken = useTelemedicineMediaToken();
  const requestInFlight = useRef(false);
  const admittedRef = useRef(false);
  const onAdmittedRef = useRef(onAdmitted);
  onAdmittedRef.current = onAdmitted;

  const requestToken = useCallback(async () => {
    if (requestInFlight.current || admittedRef.current) return;
    requestInFlight.current = true;
    try {
      const res = await mediaToken.mutateAsync({ sessionId, displayName, role });
      const payload = (res.data ?? res) as Record<string, unknown>;
      const roomUrl = String(payload.room_url ?? payload.roomUrl ?? "");
      const token = String(payload.token ?? payload.accessToken ?? "");
      if (roomUrl && token) {
        admittedRef.current = true;
        onAdmittedRef.current({
          roomUrl,
          token,
          mediaProfile: payload.mediaProfile ? String(payload.mediaProfile) : undefined,
        });
        return;
      }
      const status = String(payload.status ?? "").toUpperCase();
      if (status === "DENIED") {
        setStage("denied");
        return;
      }
      // WAITING (or any credential-less answer): keep the person informed and keep polling.
      setStage("waiting");
    } catch {
      // Network hiccup — stay in the waiting room; the next poll retries.
      setStage((current) => (current === "device_check" ? "waiting" : current));
    } finally {
      requestInFlight.current = false;
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- mutateAsync identity is stable enough for our poll loop
  }, [sessionId, displayName, role]);

  // Poll while waiting — the care team admits from their console.
  useEffect(() => {
    if (stage !== "waiting") return;
    const timer = setInterval(() => {
      void requestToken();
    }, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [stage, requestToken]);

  // Realtime accelerator: an admitted event triggers an immediate token request.
  // The admitted event may not flow yet server-side, so polling stays the truth.
  useSessionRealtime({
    sessionRef: sessionId,
    enabled: stage === "waiting",
    onEvent: (event) => {
      if (event.type === "session.admitted") {
        void requestToken();
      }
    },
  });

  if (stage === "denied") {
    return (
      <div data-testid="patient-waiting-denied" className="max-w-lg mx-auto text-center py-12 space-y-3">
        <HeartHandshake className="w-12 h-12 mx-auto text-muted-foreground" />
        <h2 className="text-lg font-semibold text-foreground">We&apos;re sorry — you can&apos;t join right now</h2>
        <p className="text-sm text-muted-foreground">
          The care team wasn&apos;t able to let you into this visit. This can happen when the
          appointment has moved or the visit is already closed. Please contact your clinic
          to rebook — your place in their care hasn&apos;t changed.
        </p>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto space-y-4" data-testid="patient-waiting-room">
      {/* What happens next */}
      <div className="bg-card rounded-xl border border-border p-4 space-y-2">
        <h2 className="text-sm font-semibold text-foreground flex items-center gap-2">
          <Clock className="w-4 h-4 text-primary" /> What happens next
        </h2>
        <ol className="text-sm text-muted-foreground list-decimal list-inside space-y-1">
          <li>Check that your camera and microphone work below.</li>
          <li>Tell the care team you&apos;re ready — they will see you in their waiting room.</li>
          <li>When they let you in, the visit starts automatically. Stay on this page.</li>
        </ol>
        <p className="text-xs text-muted-foreground">
          {specialty
            ? `A clinician from ${specialty} will join you.`
            : "Your clinician or a member of the care team will join you."}
        </p>
      </div>

      {/* Consent status — plain language */}
      <div className="bg-card rounded-xl border border-border p-4 flex items-center gap-2 text-sm">
        {consentGranted ? (
          <>
            <ShieldCheck className="w-4 h-4 text-green-600 shrink-0" />
            <span className="text-green-700">Your permission for this video visit is recorded.</span>
          </>
        ) : (
          <>
            <ShieldQuestion className="w-4 h-4 text-warning-foreground shrink-0" />
            <span className="text-muted-foreground">
              Your permission for this visit hasn&apos;t been recorded yet — the care team will confirm it with you.
            </span>
          </>
        )}
      </div>

      {stage === "device_check" && (
        <div className="space-y-2">
          <PreJoinPanel
            defaults={{ username: displayName }}
            joinLabel="I'm ready — join the visit"
            onSubmit={() => {
              setDeviceError(null);
              setStage("waiting");
              void requestToken();
            }}
            onError={() =>
              setDeviceError(
                "We couldn't reach your camera or microphone. Check your browser permissions and try again.",
              )
            }
          />
          {deviceError && (
            <p className="text-xs text-warning-foreground" data-testid="patient-device-error">{deviceError}</p>
          )}
        </div>
      )}

      {stage === "waiting" && (
        <div
          data-testid="patient-waiting-message"
          className="bg-card rounded-xl border-2 border-primary/25 p-6 text-center space-y-3"
        >
          {mediaToken.isPending ? (
            <Loader2 className="w-8 h-8 mx-auto animate-spin text-primary" />
          ) : (
            <CheckCircle2 className="w-8 h-8 mx-auto text-primary" />
          )}
          <h2 className="text-base font-semibold text-foreground">The care team knows you&apos;re here</h2>
          <p className="text-sm text-muted-foreground">
            You&apos;re in the waiting room. As soon as the care team lets you in, your visit
            will start on this page — no need to refresh or click anything.
          </p>
        </div>
      )}
    </div>
  );
}
