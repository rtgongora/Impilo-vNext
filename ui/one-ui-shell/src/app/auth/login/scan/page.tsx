"use client";

/**
 * Biometric scan-to-login — ABIS 1:N identification (L3).
 * Route: /auth/login/scan | pageTitle: "Scan to sign in"
 *
 * The HIGHEST-RISK sign-in path. A fresh capture (fingerprint or face image) is
 * sent to the BFF, which identifies it 1:N against ABIS and mints a session ONLY
 * on a single strong unambiguous match (via Keycloak token exchange). This page
 * never decides anything and never fabricates a session — it forwards the capture
 * and honours whatever the BFF returns:
 *   • success            → setAuth + continue to /auth/resolving
 *   • 401 NO_MATCH       → "couldn't identify you — use another sign-in method"
 *   • 501 NOT_ENABLED    → honest "not enabled on this deployment" state
 *   • 503 UNAVAILABLE    → "temporarily unavailable"
 *
 * Honest UX: the feature is flag-gated OFF in the BFF; with the flag off this page
 * shows the "not enabled" state as soon as a capture is submitted. No placeholder
 * biometric, no fake success.
 */

import { useState, type ChangeEvent } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Fingerprint, ScanFace, Loader2, Upload } from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { useBiometricIdentifyLogin } from "@/hooks/queries/useAuth";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useWorkModeStore } from "@/hooks/useWorkModeStore";
import { useConsentStore } from "@/hooks/useConsentStore";
import { buildPostLoginResolvingPath } from "@/lib/resolve-post-login-destination";

type Modality = "FINGERPRINT" | "FACE";

/** Strip the `data:*;base64,` prefix a FileReader data URL carries. */
function toBase64(dataUrl: string): string {
  const comma = dataUrl.indexOf(",");
  return comma >= 0 ? dataUrl.slice(comma + 1) : dataUrl;
}

function errStatus(err: unknown): number | undefined {
  return (err as { status?: number })?.status;
}
function errCode(err: unknown): string | undefined {
  return (err as { error?: { code?: string } })?.error?.code;
}

export default function BiometricScanLoginPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const returnTo = searchParams.get("returnTo");
  const identifyLogin = useBiometricIdentifyLogin();
  const { setAuth } = useAuthStore();

  const [modality, setModality] = useState<Modality>("FINGERPRINT");
  const [sampleBase64, setSampleBase64] = useState<string | null>(null);
  const [fileName, setFileName] = useState<string | null>(null);
  const [notEnabled, setNotEnabled] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function onFileChange(e: ChangeEvent<HTMLInputElement>) {
    setError(null);
    setNotEnabled(false);
    const file = e.target.files?.[0];
    if (!file) return;
    setFileName(file.name);
    const reader = new FileReader();
    reader.onload = () => setSampleBase64(toBase64(String(reader.result ?? "")));
    reader.onerror = () => setError("Could not read that file. Please try another image.");
    reader.readAsDataURL(file);
  }

  function submit() {
    if (!sampleBase64) {
      setError("Please capture or upload an image first.");
      return;
    }
    setError(null);
    setNotEnabled(false);
    identifyLogin.mutate(
      { modality, sampleBase64 },
      {
        onSuccess: (res) => {
          const attrs = res.data.attributes;
          const { token, user } = attrs;
          setAuth(
            {
              id: user.id,
              healthId: (user as { healthId?: string }).healthId ?? user.id,
              email: user.email,
              displayName: user.displayName,
              roles: user.roles,
              actorType: user.actorType as "PROVIDER" | "OPERATOR" | "CITIZEN" | "SYSTEM",
              assuranceLevel: "VERIFIED",
              providerActivated: false,
              loginMethod: "biometric",
            },
            token,
            null,
            (attrs as Record<string, unknown>).expiresAt as string | undefined,
          );
          useWorkModeStore.getState().deriveFromRoles(user.roles);
          useConsentStore.getState().hydrate(user.id);
          router.push(buildPostLoginResolvingPath(returnTo));
        },
        onError: (err: unknown) => {
          const status = errStatus(err);
          const code = errCode(err);
          if (status === 501 || code === "BIOMETRIC_LOGIN_NOT_ENABLED") {
            setNotEnabled(true);
          } else if (status === 503 || code === "BIOMETRIC_UNAVAILABLE") {
            setError("Biometric sign-in is temporarily unavailable. Please try another method.");
          } else {
            // 401 BIOMETRIC_NO_MATCH (weak / ambiguous / no match) — honest, non-committal.
            setError("We couldn't identify you. Please use another sign-in method.");
          }
        },
      },
    );
  }

  const busy = identifyLogin.isPending;
  const ModalityIcon = modality === "FACE" ? ScanFace : Fingerprint;

  return (
    <AuthLayout>
      <div className="mb-4">
        <Link
          href="/auth/login"
          className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          Back to sign in
        </Link>
      </div>

      <h2 className="text-xl font-semibold text-foreground mb-1">Scan to sign in</h2>
      <p className="text-sm text-muted-foreground mb-6">
        Scan your fingerprint or face. We&apos;ll find your Impilo ID — no password needed.
      </p>

      {error && (
        <div
          data-testid="scan-error"
          className="mb-4 p-3 rounded-lg bg-danger-soft border border-danger/28 text-sm text-danger"
        >
          {error}
        </div>
      )}

      {/* Modality selector */}
      <div className="flex gap-3 mb-5" role="group" aria-label="Biometric modality">
        <button
          type="button"
          data-testid="scan-modality-fingerprint"
          aria-pressed={modality === "FINGERPRINT"}
          onClick={() => setModality("FINGERPRINT")}
          className={`flex-1 flex items-center justify-center gap-2 py-2.5 rounded-lg border text-sm transition-colors ${
            modality === "FINGERPRINT"
              ? "border-primary bg-primary-soft text-primary"
              : "border-border text-foreground hover:bg-background"
          }`}
        >
          <Fingerprint className="w-4 h-4" />
          Fingerprint
        </button>
        <button
          type="button"
          data-testid="scan-modality-face"
          aria-pressed={modality === "FACE"}
          onClick={() => setModality("FACE")}
          className={`flex-1 flex items-center justify-center gap-2 py-2.5 rounded-lg border text-sm transition-colors ${
            modality === "FACE"
              ? "border-primary bg-primary-soft text-primary"
              : "border-border text-foreground hover:bg-background"
          }`}
        >
          <ScanFace className="w-4 h-4" />
          Face
        </button>
      </div>

      <div className="flex flex-col items-center py-4">
        <div className="w-32 h-32 rounded-full border-4 border-border bg-background text-primary flex items-center justify-center">
          {busy ? <Loader2 className="w-16 h-16 animate-spin" /> : <ModalityIcon className="w-16 h-16" />}
        </div>

        {notEnabled && (
          <div
            data-testid="scan-not-enabled"
            className="mt-6 w-full rounded-lg border border-border bg-muted/40 p-4 text-center"
          >
            <p className="text-sm font-medium text-foreground">Not available yet</p>
            <p className="mt-1 text-xs text-muted-foreground">
              Biometric scan-to-login isn&apos;t enabled on this deployment yet. Please sign in with your
              password or another method for now.
            </p>
          </div>
        )}
      </div>

      {!notEnabled && (
        <>
          {/* Capture: an honest image upload standing in for an attached capture device. */}
          <label
            htmlFor="scan-capture"
            data-testid="scan-capture-label"
            className="w-full mb-3 flex items-center justify-center gap-2 py-2.5 border border-dashed border-border rounded-lg text-sm text-foreground hover:border-primary/40 hover:bg-primary-soft cursor-pointer transition-colors"
          >
            <Upload className="w-4 h-4 text-primary" />
            {fileName ? `Captured: ${fileName}` : `Upload a ${modality === "FACE" ? "face" : "fingerprint"} image`}
          </label>
          <input
            id="scan-capture"
            data-testid="scan-capture-input"
            type="file"
            accept="image/*"
            capture="environment"
            className="sr-only"
            onChange={onFileChange}
          />

          <button
            type="button"
            data-testid="scan-signin"
            disabled={busy || !sampleBase64}
            onClick={submit}
            className="w-full py-2.5 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover focus:outline-none focus:ring-2 focus:ring-primary/40 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
          >
            {busy ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                Identifying…
              </>
            ) : (
              <>
                <Fingerprint className="w-4 h-4" />
                Scan and sign in
              </>
            )}
          </button>
        </>
      )}

      <Link
        href="/auth/login"
        className="mt-3 w-full py-2.5 border border-border text-foreground text-sm font-medium rounded-lg hover:bg-background focus:outline-none focus:ring-2 focus:ring-primary/40 flex items-center justify-center gap-2 transition-colors"
      >
        Use another sign-in method
      </Link>
    </AuthLayout>
  );
}
