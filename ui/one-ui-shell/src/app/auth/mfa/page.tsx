"use client";

import { useEffect } from "react";
import { useSearchParams } from "next/navigation";
import { Loader2, ShieldCheck } from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { beginOidcLogin } from "@/lib/auth/web-session";

/**
 * Compatibility entry point for old MFA links. Challenges are rendered and
 * verified by Keycloak; Impilo never receives an OTP or recovery code.
 */
export default function MfaPage() {
  const searchParams = useSearchParams();

  useEffect(() => {
    beginOidcLogin({
      returnTo: searchParams.get("returnTo") ?? "/home",
      requiredAcr: "urn:impilo:aal2",
    });
  }, [searchParams]);

  return (
    <AuthLayout>
      <div className="flex flex-col items-center py-10 text-center">
        <ShieldCheck className="h-10 w-10 text-primary" aria-hidden="true" />
        <h2 className="mt-4 text-lg font-semibold text-foreground">Opening secure verification</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          Your authenticator, passkey or recovery code is verified only by Keycloak.
        </p>
        <Loader2 className="mt-5 h-5 w-5 animate-spin text-primary" aria-hidden="true" />
      </div>
    </AuthLayout>
  );
}
