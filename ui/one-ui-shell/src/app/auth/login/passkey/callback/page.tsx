"use client";

import { useEffect } from "react";
import { Loader2 } from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { beginOidcLogin } from "@/lib/auth/web-session";

/** Retired duplicate callback; discard any legacy code and start the BFF flow. */
export default function PasskeyCallbackPage() {
  useEffect(() => {
    beginOidcLogin({ requiredAcr: "urn:impilo:aal2", returnTo: "/home" });
  }, []);

  return (
    <AuthLayout>
      <div className="flex flex-col items-center py-10 text-center">
        <Loader2 className="h-10 w-10 animate-spin text-primary" aria-hidden="true" />
        <h2 className="mt-4 text-lg font-semibold text-foreground">Opening secure passkey sign-in…</h2>
      </div>
    </AuthLayout>
  );
}
