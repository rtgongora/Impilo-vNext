"use client";

import { useEffect } from "react";
import { Loader2 } from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";

/**
 * The old form posted a permanent password to Impilo. Registration now
 * converges on verified email plus a native Keycloak UPDATE_PASSWORD action,
 * while preserving the complete trust-escalation query string.
 */
export default function RegisterPage() {
  useEffect(() => {
    window.location.replace(`/auth/register/contact${window.location.search}`);
  }, []);

  return (
    <AuthLayout>
      <div className="flex items-center justify-center gap-2 py-8 text-sm text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
        <span>Opening secure account creation…</span>
      </div>
    </AuthLayout>
  );
}
