"use client";

/**
 * Biometric Verification — sign-in entry point.
 * Route: /auth/login/biometric | pageTitle: "Biometric Verification"
 *
 * Honest state (G-CZO-14): device-biometric sign-in (WebAuthn/passkeys) is not yet wired to a
 * server-side challenge/verification. Rather than simulate a verification and sign in with placeholder
 * credentials (which only ever 401s and shows a cosmetic "success"), this page tells the user the
 * method is unavailable and routes them to a real sign-in method.
 */

import { ArrowLeft, Fingerprint } from "lucide-react";
import Link from "next/link";
import { AuthLayout } from "@/components/AuthLayout";

export default function BiometricLoginPage() {
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

      <h2 className="text-xl font-semibold text-foreground mb-1">Biometric verification</h2>
      <p className="text-sm text-muted-foreground mb-6">Sign in with your device biometrics</p>

      <div className="flex flex-col items-center py-6">
        <div className="w-32 h-32 rounded-full border-4 border-border bg-background text-muted-foreground flex items-center justify-center">
          <Fingerprint className="w-16 h-16" />
        </div>

        <div className="mt-6 w-full rounded-lg border border-border bg-muted/40 p-4 text-center">
          <p className="text-sm font-medium text-foreground">Not available yet</p>
          <p className="mt-1 text-xs text-muted-foreground">
            Biometric sign-in (passkeys / WebAuthn) isn&apos;t enabled on this deployment yet. Please
            sign in with your password or another method for now.
          </p>
        </div>
      </div>

      <Link
        href="/auth/login"
        className="w-full py-2.5 bg-primary text-white text-sm font-medium rounded-lg hover:bg-primary-hover focus:outline-none focus:ring-2 focus:ring-primary/40 focus:ring-offset-2 flex items-center justify-center gap-2 transition-colors"
      >
        Use another sign-in method
      </Link>
    </AuthLayout>
  );
}
