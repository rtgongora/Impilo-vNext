import { useCallback, useState } from "react";
import { apiClient } from "@/lib/api-client";

/**
 * Drives the citizen step-up completion loop (G-CZO-04 / G-CZO-17) against the BFF
 * proxy (`/internal/v1/step-up/*` → tshepo-authz). Issue a challenge for a chosen
 * method, submit the verification code, and on success the caller retries the
 * original sensitive action — PolicyEngine then sees a recently-completed step-up
 * and allows it. Identity is bound server-side from trust headers, never here.
 */
type Phase = "idle" | "challenging" | "ready" | "verifying";

interface ChallengeResponse {
  data?: { challengeId?: string; status?: string };
  challengeId?: string;
  status?: string;
}

export interface UseStepUp {
  phase: Phase;
  error: string | null;
  /** Issue a challenge for the chosen method (e.g. "MFA", "SMS_OTP", "BIOMETRIC"). */
  startChallenge: (method: string) => Promise<boolean>;
  /** Submit the verification code; resolves true when the challenge is COMPLETED. */
  submitCode: (code: string) => Promise<boolean>;
  reset: () => void;
}

function challengeId(r: ChallengeResponse): string | undefined {
  return r?.data?.challengeId ?? r?.challengeId;
}
function status(r: ChallengeResponse): string | undefined {
  return r?.data?.status ?? r?.status;
}

export function useStepUp(): UseStepUp {
  const [phase, setPhase] = useState<Phase>("idle");
  const [error, setError] = useState<string | null>(null);
  const [challenge, setChallenge] = useState<string | null>(null);

  const startChallenge = useCallback(async (method: string): Promise<boolean> => {
    setError(null);
    setPhase("challenging");
    try {
      const res = await apiClient.post<ChallengeResponse>("/internal/v1/step-up/challenge", {
        challengeType: method,
      });
      const id = challengeId(res);
      if (!id) {
        setError("Could not start verification. Please try again.");
        setPhase("idle");
        return false;
      }
      setChallenge(id);
      setPhase("ready");
      return true;
    } catch {
      setError("Could not start verification. Please try again.");
      setPhase("idle");
      return false;
    }
  }, []);

  const submitCode = useCallback(
    async (code: string): Promise<boolean> => {
      if (!challenge) {
        setError("Start verification first.");
        return false;
      }
      setError(null);
      setPhase("verifying");
      try {
        const res = await apiClient.post<ChallengeResponse>("/internal/v1/step-up/verify", {
          challengeId: challenge,
          verificationCode: code,
        });
        if (status(res) === "COMPLETED") {
          setPhase("idle");
          setChallenge(null);
          return true;
        }
        setError("Verification did not complete. Please try again.");
        setPhase("ready");
        return false;
      } catch {
        setError("That code was not accepted. Please try again.");
        setPhase("ready");
        return false;
      }
    },
    [challenge],
  );

  const reset = useCallback(() => {
    setPhase("idle");
    setError(null);
    setChallenge(null);
  }, []);

  return { phase, error, startChallenge, submitCode, reset };
}
