"use client";

import Link from "next/link";
import { useCallback, useState } from "react";
import { Button, Card, CardHeader, CardTitle } from "shared-ui";

import type { AuthzResponse } from "@/lib/contracts";
import { apiClient } from "@/lib/apiClient";
import { useSession } from "@/hooks/useSession";
import { useWorkContext } from "@/hooks/useContext";
import { clearPersistedShellSession } from "@/lib/auth/sessionStorage";
import {
  getKeycloakClientId,
  getKeycloakIssuerUrl,
  getShellPublicUrl,
} from "@/lib/auth/shellOidcEnv";
import { isLikelyJwt } from "@/lib/auth/isLikelyJwt";

const linkSecondarySm =
  "inline-flex items-center justify-center rounded-[12px] border border-neutral-500/20 bg-neutral-100 px-3 py-1.5 text-xs font-medium text-neutral-900 transition-colors hover:bg-neutral-100/80 focus:outline-none focus:ring-2 focus:ring-offset-2";

export function DashboardClient() {
  const session = useSession((s) => s.session);
  const workContext = useWorkContext((s) => s.workContext);
  const oidcSession = Boolean(session?.accessToken && isLikelyJwt(session.accessToken));

  const [policyLoading, setPolicyLoading] = useState(false);
  const [policyJson, setPolicyJson] = useState<string | null>(null);
  const [policyError, setPolicyError] = useState<string | null>(null);

  const runQuickPolicyCheck = useCallback(async () => {
    if (!oidcSession || !session) return;
    setPolicyLoading(true);
    setPolicyJson(null);
    setPolicyError(null);
    try {
      const response = await apiClient.post<AuthzResponse>(
        "/api/v1/authorize",
        {
          action: "POST:/api/v1/authorize",
          resourceType: "authorize",
        },
        { purposeOfUse: useWorkContext.getState().purposeOfUse }
      );
      setPolicyJson(JSON.stringify(response.data, null, 2));
    } catch (err: unknown) {
      setPolicyError(
        err && typeof err === "object"
          ? JSON.stringify(err, null, 2)
          : String(err)
      );
    } finally {
      setPolicyLoading(false);
    }
  }, [oidcSession, session]);

  const signOut = useCallback(() => {
    const s = useSession.getState().session;
    const hadJwt = Boolean(s?.accessToken && isLikelyJwt(s.accessToken));
    useSession.getState().clearSession();
    useWorkContext.getState().clearContext();
    clearPersistedShellSession();
    if (!hadJwt) return;
    const shell = getShellPublicUrl();
    const issuer = getKeycloakIssuerUrl();
    const clientId = getKeycloakClientId();
    const logout = new URL(`${issuer}/protocol/openid-connect/logout`);
    logout.searchParams.set("client_id", clientId);
    logout.searchParams.set("post_logout_redirect_uri", `${shell}/`);
    window.location.assign(logout.toString());
  }, []);

  if (!oidcSession) {
    return (
      <div className="min-h-screen bg-neutral-50 text-neutral-900">
        <main className="mx-auto max-w-xl px-6 py-20">
          <Card>
            <CardHeader>
              <CardTitle>Not signed in</CardTitle>
            </CardHeader>
            <p className="px-6 pb-2 text-sm text-neutral-600">
              You need a Keycloak session to use the dashboard. This page does not use
              developer-only manual identity—that lives under{" "}
              <Link
                href="/dev/runtime"
                className="font-medium text-brand-primary hover:underline"
              >
                /dev/runtime
              </Link>
              .
            </p>
            <div className="flex flex-wrap gap-3 px-6 pb-6">
              <a
                href="/api/auth/login"
                className="inline-flex items-center justify-center rounded-[12px] bg-brand-primary px-5 py-2.5 text-sm font-semibold text-white hover:bg-brand-primary/90"
              >
                Sign in
              </a>
              <Link href="/" className={linkSecondarySm}>
                Back to home
              </Link>
            </div>
          </Card>
        </main>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-neutral-50 text-neutral-900">
      <header className="border-b border-neutral-100 bg-white">
        <div className="mx-auto flex max-w-3xl flex-wrap items-center justify-between gap-4 px-6 py-5">
          <div>
            <p className="text-xs font-medium text-brand-primary">Impilo One UI Shell</p>
            <h1 className="text-xl font-semibold text-neutral-900">Dashboard</h1>
          </div>
          <div className="flex flex-wrap gap-2">
            <Link href="/" className={linkSecondarySm}>
              Home
            </Link>
            <Button variant="secondary" size="sm" type="button" onClick={signOut}>
              Sign out
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-3xl space-y-6 px-6 py-8">
        <Card>
          <CardHeader>
            <CardTitle>Signed in</CardTitle>
          </CardHeader>
          <dl className="grid gap-3 px-6 pb-6 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-xs font-medium text-neutral-500">Name</dt>
              <dd className="text-neutral-900">{session!.displayName}</dd>
            </div>
            {session!.email && (
              <div>
                <dt className="text-xs font-medium text-neutral-500">Email</dt>
                <dd className="text-neutral-900">{session!.email}</dd>
              </div>
            )}
            <div>
              <dt className="text-xs font-medium text-neutral-500">Actor type</dt>
              <dd className="text-neutral-900">
                {session!.actorType}
                {session!.actorTypeSource === "default_no_roles" && (
                  <span className="mt-1 block text-xs font-normal text-neutral-500">
                    Role fallback: no role claim in token (using PROVIDER for
                    actor type).
                  </span>
                )}
              </dd>
            </div>
            <div>
              <dt className="text-xs font-medium text-neutral-500">Actor id</dt>
              <dd className="break-all text-neutral-900">{session!.actorId}</dd>
            </div>
            <div className="sm:col-span-2">
              <dt className="text-xs font-medium text-neutral-500">Realm roles (from token)</dt>
              <dd className="text-neutral-900">
                {session!.roles.length ? session!.roles.join(", ") : "—"}
              </dd>
            </div>
          </dl>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Work context</CardTitle>
          </CardHeader>
          <dl className="grid gap-3 px-6 pb-6 text-sm sm:grid-cols-2">
            <div>
              <dt className="text-xs font-medium text-neutral-500">Tenant (x-tenant-id)</dt>
              <dd className="break-all text-neutral-900">
                {workContext?.tenantId ?? "—"}
                {workContext?.tenantIdHeaderOrigin === "fallback_uuid_no_claim" && (
                  <span className="mt-1 block text-xs text-amber-800">
                    Fallback UUID: token had no tenant_id claim.
                  </span>
                )}
              </dd>
            </div>
            <div>
              <dt className="text-xs font-medium text-neutral-500">Facility</dt>
              <dd className="break-all text-neutral-900">
                {workContext?.facilityId ?? "—"}
              </dd>
            </div>
            <div>
              <dt className="text-xs font-medium text-neutral-500">Workspace</dt>
              <dd className="break-all text-neutral-900">
                {workContext?.workspaceId ?? "—"}
              </dd>
            </div>
          </dl>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>TSHEPO policy</CardTitle>
          </CardHeader>
          <p className="px-6 text-sm text-neutral-600">
            Run a quick authorization check with your current bearer and trust headers, or
            open the full diagnostic page for editable vectors and detailed responses.
          </p>
          <div className="flex flex-wrap gap-2 px-6 pb-4 pt-4">
            <Button
              type="button"
              onClick={runQuickPolicyCheck}
              disabled={policyLoading}
            >
              {policyLoading ? "Calling TSHEPO…" : "Run TSHEPO policy check"}
            </Button>
            <Link href="/dev/runtime" className={linkSecondarySm}>
              Open runtime diagnostics
            </Link>
          </div>
          {policyJson && (
            <pre className="mx-6 mb-6 max-h-48 overflow-auto rounded-lg bg-neutral-900 p-4 text-xs text-neutral-50">
              {policyJson}
            </pre>
          )}
          {policyError && (
            <pre className="mx-6 mb-6 max-h-48 overflow-auto rounded-lg border border-danger/40 bg-red-50 p-4 text-xs text-danger">
              {policyError}
            </pre>
          )}
        </Card>
      </main>
    </div>
  );
}
