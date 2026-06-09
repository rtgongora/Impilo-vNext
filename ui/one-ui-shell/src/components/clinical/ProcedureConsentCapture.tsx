"use client";

import { useState } from "react";
import { CheckCircle2, Loader2, Shield, Smartphone } from "lucide-react";
import {
  useProcedureConsentCapture,
  useProcedureConsentLive,
} from "@/hooks/queries/useProcedureConsentCapture";
import type { ProcedureEpisode } from "@/hooks/queries/useProcedureEpisode";

const LIFECYCLE_STEPS = [
  { key: "PENDING_EXPLANATION", label: "1. Explanation", action: "explanation" as const },
  { key: "EXPLANATION_PROVIDED", label: "2. Identity", action: "identity" as const },
  { key: "PENDING_SIGNATURE", label: "3. Grant", action: "grant" as const },
];

type Props = {
  episodeId: string;
  episode: ProcedureEpisode;
};

export function ProcedureConsentCapture({ episodeId, episode }: Props) {
  const capture = useProcedureConsentCapture(episodeId);
  const live = useProcedureConsentLive(episodeId, episode.mvumo_consent_request_id);
  const [mode, setMode] = useState<"facility" | "remote">("facility");
  const [explanationNotes, setExplanationNotes] = useState(
    "Risks, benefits, alternatives, and recovery expectations explained for this procedure."
  );
  const [remoteSessionId, setRemoteSessionId] = useState("");
  const [remoteToken, setRemoteToken] = useState("");
  const [sessionTokenDisplay, setSessionTokenDisplay] = useState<string | null>(null);

  const mvumoState = String(
    live.data?.mvumo?.state ?? episode.consent_status ?? "NOT_REQUESTED"
  );
  const isGranted = mvumoState === "GRANTED" || episode.consent_status === "GRANTED";
  const isPending = capture.requestConsent.isPending
    || capture.provideExplanation.isPending
    || capture.verifyIdentity.isPending
    || capture.grantConsent.isPending
    || capture.createRemoteSession.isPending
    || capture.verifyRemoteSession.isPending
    || capture.grantRemoteSession.isPending;

  const stepIndex = (() => {
    if (mvumoState === "GRANTED" || mvumoState === "REFUSED") return 3;
    if (mvumoState === "PENDING_SIGNATURE") return 2;
    if (mvumoState === "EXPLANATION_PROVIDED" || mvumoState === "PENDING_IDENTITY_VERIFICATION") return 1;
    if (episode.mvumo_consent_request_id) return 0;
    return -1;
  })();

  return (
    <div className="rounded-xl border p-4 space-y-4">
      <div className="flex items-center gap-2">
        <Shield className="h-5 w-5 text-impilo-500" />
        <h3 className="font-medium">MVUMO surgical consent</h3>
      </div>

      <p className="text-sm text-gray-600">
        Live MVUMO state: <span className="font-semibold text-gray-900">{mvumoState}</span>
        {episode.consent_proof_ref ? (
          <span className="block text-xs text-gray-500 mt-1">Proof: {episode.consent_proof_ref}</span>
        ) : null}
      </p>

      {isGranted ? (
        <p className="text-sm text-green-700 flex items-center gap-1">
          <CheckCircle2 className="h-4 w-4" />
          Consent granted with Tshepo evidence — theatre start enabled
        </p>
      ) : (
        <>
          {!episode.mvumo_consent_request_id ? (
            <button
              type="button"
              disabled={capture.requestConsent.isPending}
              onClick={() => capture.requestConsent.mutate()}
              className="rounded-lg bg-impilo-500 px-4 py-2 text-sm text-white disabled:opacity-50"
            >
              {capture.requestConsent.isPending ? "Creating…" : "Create MVUMO consent request"}
            </button>
          ) : (
            <>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setMode("facility")}
                  className={`rounded-full px-3 py-1 text-xs ${mode === "facility" ? "bg-impilo-500 text-white" : "bg-gray-100"}`}
                >
                  Facility-assisted
                </button>
                <button
                  type="button"
                  onClick={() => setMode("remote")}
                  className={`rounded-full px-3 py-1 text-xs ${mode === "remote" ? "bg-impilo-500 text-white" : "bg-gray-100"}`}
                >
                  Remote session
                </button>
              </div>

              {mode === "facility" ? (
                <div className="space-y-3">
                  <div className="flex flex-wrap gap-2">
                    {LIFECYCLE_STEPS.map((step, i) => (
                      <span
                        key={step.key}
                        className={`rounded-full px-2 py-0.5 text-xs ${
                          i <= stepIndex ? "bg-green-100 text-green-800" : "bg-gray-100 text-gray-500"
                        }`}
                      >
                        {step.label}
                      </span>
                    ))}
                  </div>

                  {stepIndex < 1 && (
                    <>
                      <label className="block text-xs text-gray-600">
                        Explanation notes
                        <textarea
                          value={explanationNotes}
                          onChange={(e) => setExplanationNotes(e.target.value)}
                          className="mt-1 w-full rounded border p-2 text-sm min-h-[72px]"
                        />
                      </label>
                      <button
                        type="button"
                        disabled={isPending}
                        onClick={() =>
                          capture.provideExplanation.mutate({
                            explanationNotes,
                            actualMethod: "FACILITY_TABLET",
                          })
                        }
                        className="rounded-lg bg-impilo-500 px-4 py-2 text-sm text-white disabled:opacity-50"
                      >
                        Record explanation (MVUMO)
                      </button>
                    </>
                  )}

                  {stepIndex === 1 && (
                    <button
                      type="button"
                      disabled={isPending}
                      onClick={() =>
                        capture.verifyIdentity.mutate({
                          identityVerified: true,
                          actualMethod: "FACILITY_WITNESSED",
                        })
                      }
                      className="rounded-lg bg-impilo-500 px-4 py-2 text-sm text-white disabled:opacity-50"
                    >
                      Verify patient identity (MVUMO)
                    </button>
                  )}

                  {stepIndex >= 2 && mvumoState !== "REFUSED" && (
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        disabled={isPending}
                        onClick={() =>
                          capture.grantConsent.mutate({
                            actualAssurance: "L3_WITNESSED_HIGH_ASSURANCE",
                            grantedByRef: "Provider/theatre-clinician",
                          })
                        }
                        className="rounded-lg bg-green-600 px-4 py-2 text-sm text-white disabled:opacity-50"
                      >
                        Grant consent (MVUMO → Tshepo)
                      </button>
                      <button
                        type="button"
                        disabled={isPending}
                        onClick={() => capture.refuseConsent.mutate("Patient declined procedure consent")}
                        className="rounded-lg border border-red-300 px-4 py-2 text-sm text-red-700 disabled:opacity-50"
                      >
                        Record refusal
                      </button>
                    </div>
                  )}
                </div>
              ) : (
                <div className="space-y-3 rounded-lg border border-violet-100 bg-violet-50/50 p-3">
                  <p className="text-xs text-violet-800 flex items-center gap-1">
                    <Smartphone className="h-3 w-3" />
                    Remote consent: patient verifies token on device, then clinician grants.
                  </p>
                  {!remoteSessionId ? (
                    <button
                      type="button"
                      disabled={isPending}
                      onClick={() =>
                        capture.createRemoteSession.mutate(
                          { channel: "TOKEN_LINK", ttlMinutes: 30 },
                          {
                            onSuccess: (res) => {
                              const raw = res.data as Record<string, unknown> | undefined;
                              const sid = String(raw?.sessionId ?? raw?.id ?? "");
                              const token = String(raw?.token ?? "");
                              if (sid) setRemoteSessionId(sid);
                              if (token) setSessionTokenDisplay(token);
                            },
                          }
                        )
                      }
                      className="rounded-lg bg-violet-600 px-4 py-2 text-sm text-white disabled:opacity-50"
                    >
                      Create remote consent session
                    </button>
                  ) : (
                    <>
                      <p className="text-xs font-mono text-gray-600">Session: {remoteSessionId}</p>
                      {sessionTokenDisplay ? (
                        <p className="text-xs break-all rounded bg-white p-2 border">
                          Patient token: <strong>{sessionTokenDisplay}</strong>
                        </p>
                      ) : null}
                      <input
                        value={remoteToken}
                        onChange={(e) => setRemoteToken(e.target.value)}
                        placeholder="Enter patient verification token"
                        className="w-full rounded border px-2 py-1 text-sm"
                      />
                      <div className="flex flex-wrap gap-2">
                        <button
                          type="button"
                          disabled={!remoteToken.trim() || capture.verifyRemoteSession.isPending}
                          onClick={() =>
                            capture.verifyRemoteSession.mutate({
                              sessionId: remoteSessionId,
                              token: remoteToken.trim(),
                            })
                          }
                          className="rounded-lg border px-3 py-1 text-sm disabled:opacity-50"
                        >
                          Verify remote session
                        </button>
                        <button
                          type="button"
                          disabled={capture.grantRemoteSession.isPending}
                          onClick={() =>
                            capture.grantRemoteSession.mutate({ sessionId: remoteSessionId })
                          }
                          className="rounded-lg bg-green-600 px-3 py-1 text-sm text-white disabled:opacity-50"
                        >
                          Grant via remote session
                        </button>
                      </div>
                    </>
                  )}
                </div>
              )}

              <button
                type="button"
                disabled={capture.syncConsent.isPending}
                onClick={() => capture.syncConsent.mutate()}
                className="text-xs text-impilo-600 hover:underline disabled:opacity-50"
              >
                {capture.syncConsent.isPending ? "Syncing…" : "Sync state from MVUMO"}
              </button>
            </>
          )}
        </>
      )}

      {isPending && <Loader2 className="h-4 w-4 animate-spin text-gray-400" />}
    </div>
  );
}
