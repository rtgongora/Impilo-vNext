"use client";

import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";

import { useSession } from "@/hooks/useSession";
import { useWorkContext } from "@/hooks/useContext";
import type { SessionInfo, WorkContext } from "@/lib/contracts";
import { savePersistedShellSession } from "@/lib/auth/sessionStorage";
import type { TokenBuildFailureDiagnostics } from "@/lib/auth/tokenSessionDiagnostics";

const DEFAULT_TENANT = "00000000-0000-4000-8000-000000000001";

export function CallbackClient() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [message, setMessage] = useState<string | null>(null);
  const [diagnostics, setDiagnostics] = useState<TokenBuildFailureDiagnostics | null>(
    null
  );

  useEffect(() => {
    const err = searchParams.get("error_description") || searchParams.get("error");
    if (err) {
      setDiagnostics(null);
      setMessage(err);
      return;
    }

    const code = searchParams.get("code");
    const state = searchParams.get("state");
    if (!code || !state) {
      setMessage("Missing authorization code or state from Keycloak.");
      return;
    }

    let cancelled = false;

    (async () => {
      try {
        const res = await fetch("/api/auth/token", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ code, state }),
        });
        let data: {
          error?: string;
          detail?: string;
          diagnostics?: TokenBuildFailureDiagnostics;
          session?: SessionInfo;
          tenantId?: string | null;
          facilityId?: string | null;
        };
        try {
          data = (await res.json()) as typeof data;
        } catch {
          setDiagnostics(null);
          setMessage(
            `Token endpoint returned a non-JSON response (HTTP ${res.status}).`
          );
          return;
        }
        if (!res.ok) {
          setDiagnostics(data.diagnostics ?? null);
          setMessage(
            data.detail ||
              data.error ||
              `Token exchange failed (${res.status}).`
          );
          return;
        }
        if (!data.session || cancelled) return;

        const fromToken = data.tenantId?.trim() ?? "";
        const usedFallback = !fromToken;
        const tenantId = usedFallback ? DEFAULT_TENANT : fromToken;
        const workContext: WorkContext = {
          tenantId,
          facilityId: data.facilityId?.trim() || undefined,
          tenantIdHeaderOrigin: usedFallback
            ? "fallback_uuid_no_claim"
            : "keycloak_access_token",
        };

        useSession.getState().setSession(data.session);
        useWorkContext.getState().setWorkContext(workContext);

        const purposeOfUse = useWorkContext.getState().purposeOfUse;
        savePersistedShellSession({
          session: data.session,
          workContext,
          purposeOfUse,
        });

        router.replace("/dashboard");
      } catch (e) {
        if (!cancelled) {
          setMessage(e instanceof Error ? e.message : String(e));
        }
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [router, searchParams]);

  if (message) {
    return (
      <div className="mx-auto max-w-lg px-4 py-16 text-center">
        <h1 className="text-lg font-semibold text-neutral-900">Sign-in failed</h1>
        <p className="mt-2 text-sm text-neutral-600">{message}</p>
        {diagnostics && (
          <pre className="mt-6 max-h-64 overflow-auto rounded-lg border border-neutral-200 bg-neutral-50 p-3 text-left text-xs text-neutral-800">
            {JSON.stringify(diagnostics, null, 2)}
          </pre>
        )}
        <a
          className="mt-6 inline-block text-sm font-medium text-brand-primary"
          href="/"
        >
          Back to home
        </a>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-lg px-4 py-16 text-center text-sm text-neutral-600">
      Completing sign-in…
    </div>
  );
}
