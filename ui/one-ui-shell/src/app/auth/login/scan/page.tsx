"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { ArrowLeft, Fingerprint, ShieldCheck } from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { beginOidcLogin } from "@/lib/auth/web-session";

/**
 * ABIS answers “which identity might this be?” and is not an authenticator.
 * The former 1:N token-minting shortcut is fail-closed; this journey completes
 * with a native Keycloak passkey instead of turning a biometric match into a
 * session.
 */
export default function BiometricScanLoginPage() {
  const searchParams = useSearchParams();

  return (
    <AuthLayout>
      <Link
        href="/auth/login"
        className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />
        Back to sign in
      </Link>

      <div className="flex flex-col items-center py-6 text-center">
        <div className="flex h-24 w-24 items-center justify-center rounded-full border-4 border-border bg-background text-primary">
          <Fingerprint className="h-12 w-12" aria-hidden="true" />
        </div>
        <h2 className="mt-5 text-xl font-semibold text-foreground">Use your device passkey</h2>
        <p className="mt-2 text-sm text-muted-foreground">
          A clinical biometric record can help confirm identity, but it cannot create a login session.
          Your device verifies you securely with Keycloak and never sends the fingerprint or face image to Impilo.
        </p>
      </div>

      <button
        type="button"
        data-testid="scan-signin"
        onClick={() => beginOidcLogin({
          requiredAcr: "urn:impilo:aal2",
          returnTo: searchParams.get("returnTo") ?? "/home",
        })}
        className="flex w-full items-center justify-center gap-2 rounded-lg bg-primary py-2.5 text-sm font-medium text-white hover:bg-primary-hover"
      >
        <ShieldCheck className="h-4 w-4" aria-hidden="true" />
        Continue with a passkey
      </button>
    </AuthLayout>
  );
}
