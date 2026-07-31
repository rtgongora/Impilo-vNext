"use client";

import { useState } from "react";
import Link from "next/link";
import { AlertCircle, ArrowLeft, KeyRound, Loader2, LogOut, RefreshCw, ShieldCheck, Smartphone } from "lucide-react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { apiClient } from "@/lib/api-client";
import { TrustGovernanceStrip } from "@/components/trust/TrustGovernanceStrip";

type Credential = { id: string; method: string; label?: string; createdAt?: string; removable: boolean };
type Session = { id: string; ipAddress: string; startedAt?: string; lastActiveAt?: string; current: boolean; clients: string[] };
type SecuritySnapshot = {
  methods: Credential[];
  sessions: Session[];
  requiredAal: string;
  workforce: boolean;
  privileged: boolean;
  currentAal?: string;
  currentMethods: string[];
  recoveryCodesConfigured: boolean;
  recoveryCodesRemaining?: number | null;
  availableActions: string[];
};
type SecurityResponse = { data: SecuritySnapshot };

const actionLabels: Record<string, { title: string; detail: string }> = {
  CONFIGURE_TOTP: { title: "Authenticator app", detail: "Scan a QR code or enter the manual setup key in Keycloak." },
  "webauthn-register": { title: "Security key", detail: "Register and name a user-verified hardware credential." },
  "webauthn-register-passwordless": { title: "Passkey", detail: "Use a device passkey for phishing-resistant sign-in." },
  CONFIGURE_RECOVERY_AUTHN_CODES: { title: "Recovery codes", detail: "Generate 12 one-use codes and confirm that you saved them." },
};

export default function SecuritySettingsPage() {
  const queryClient = useQueryClient();
  const [notice, setNotice] = useState<string>();
  const security = useQuery<SecurityResponse>({
    queryKey: ["settings-security"],
    queryFn: () => apiClient.get<SecurityResponse>("/internal/v1/settings/security"),
  });

  const beginAction = useMutation({
    mutationFn: (action: string) => apiClient.post<{ data: { authorizeUrl: string } }>(
      "/internal/v1/auth/oidc/action", { action, returnTo: "/settings/security" }),
    onSuccess: (response) => window.location.assign(response.data.authorizeUrl),
  });
  const stepUp = useMutation({
    mutationFn: () => apiClient.post<{ data: { authorizeUrl: string } }>(
      "/internal/v1/auth/oidc/step-up", { requiredAcr: "urn:impilo:aal2", returnTo: "/settings/security" }),
    onSuccess: (response) => window.location.assign(response.data.authorizeUrl),
  });
  const remove = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/internal/v1/settings/security/credentials/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["settings-security"] }),
    onError: () => setNotice("Verify again at AAL2, then retry removing the method."),
  });
  const revoke = useMutation({
    mutationFn: (id: string) => apiClient.delete(`/internal/v1/settings/security/sessions/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["settings-security"] }),
    onError: () => setNotice("A fresh AAL2 verification is required to revoke a session."),
  });
  const logoutOthers = useMutation({
    mutationFn: () => apiClient.post("/internal/v1/settings/security/sessions/logout-others", {}),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["settings-security"] }),
    onError: () => setNotice("A fresh AAL2 verification is required to revoke other sessions."),
  });

  const data = security.data?.data;
  return (
    <AppLayout>
      <PageShell title="Account security" subtitle="Sign-in methods, recovery and active sessions are governed by Keycloak">
        <div className="mb-4">
          <Link href="/settings" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
            <ArrowLeft className="h-4 w-4" /> Back to settings
          </Link>
        </div>
        {security.isLoading ? (
          <div className="flex items-center gap-2 py-16"><Loader2 className="h-5 w-5 animate-spin" /> Loading account security…</div>
        ) : security.error || !data ? (
          <div className="rounded-xl border border-danger/30 bg-danger-soft p-6 text-danger">
            <AlertCircle className="mb-2 h-5 w-5" /> Account security could not be loaded. No setting was changed.
          </div>
        ) : (
          <div className="max-w-3xl space-y-6">
            {data.privileged && <TrustGovernanceStrip />}
            <section className="rounded-xl border border-border bg-card p-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div><h2 className="font-semibold">Current assurance</h2><p className="text-sm text-muted-foreground">{data.currentAal ?? "Not reported"} · {data.currentMethods.join(", ") || "method not reported"}</p></div>
                <span className="rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">Required: {data.requiredAal}</span>
              </div>
              {data.workforce && data.methods.length === 0 && <p className="mt-4 rounded-lg bg-warning-soft p-3 text-sm text-warning-foreground">Workforce access requires MFA. Enrol a method before continuing work.</p>}
            </section>

            <section className="rounded-xl border border-border bg-card p-5">
              <h2 className="font-semibold">Sign-in and recovery methods</h2>
              <p className="mt-1 text-sm text-muted-foreground">Setup happens on the trusted Keycloak page. Impilo never receives your secret or recovery codes.</p>
              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                {Object.entries(actionLabels).map(([action, copy]) => (
                  <button key={action} onClick={() => beginAction.mutate(action)} disabled={beginAction.isPending}
                    className="rounded-xl border border-border p-4 text-left hover:border-primary/40 hover:bg-primary/5 disabled:opacity-50">
                    <div className="flex items-center gap-2 font-medium"><KeyRound className="h-4 w-4 text-primary" />{copy.title}</div>
                    <p className="mt-2 text-xs text-muted-foreground">{copy.detail}</p>
                  </button>
                ))}
              </div>
              <div className="mt-5 space-y-2">
                {data.methods.length === 0 ? <p className="text-sm text-muted-foreground">No MFA credential is enrolled.</p> : data.methods.map((method) => (
                  <div key={method.id} className="flex items-center justify-between gap-3 rounded-lg border border-border px-4 py-3">
                    <div><p className="text-sm font-medium capitalize">{method.label || method.method}</p><p className="text-xs text-muted-foreground">{method.method}{method.createdAt ? ` · added ${new Date(method.createdAt).toLocaleDateString()}` : ""}</p></div>
                    {method.removable && <button onClick={() => remove.mutate(method.id)} className="text-xs font-medium text-danger hover:underline">Remove</button>}
                  </div>
                ))}
              </div>
              <p className="mt-3 text-xs text-muted-foreground">Recovery codes: {data.recoveryCodesConfigured ? (data.recoveryCodesRemaining == null ? "configured; remaining count is shown only by Keycloak" : `${data.recoveryCodesRemaining} remaining`) : "not configured"}</p>
              <button onClick={() => beginAction.mutate("UPDATE_PASSWORD")} className="mt-4 inline-flex items-center gap-2 text-sm font-medium text-primary hover:underline"><RefreshCw className="h-4 w-4" /> Change password securely</button>
            </section>

            <section className="rounded-xl border border-border bg-card">
              <div className="flex items-center justify-between border-b border-border px-5 py-4"><div><h2 className="font-semibold">Active sessions</h2><p className="text-xs text-muted-foreground">Revoke a lost or unfamiliar session immediately.</p></div><button onClick={() => logoutOthers.mutate()} className="text-xs font-medium text-danger hover:underline">Log out other sessions</button></div>
              <div className="divide-y divide-border">
                {data.sessions.length === 0 ? <p className="p-5 text-sm text-muted-foreground">No active Keycloak sessions were returned.</p> : data.sessions.map((session) => (
                  <div key={session.id} className="flex items-center justify-between gap-3 p-5">
                    <div className="flex items-start gap-3"><Smartphone className="mt-0.5 h-5 w-5 text-muted-foreground" /><div><p className="text-sm font-medium">{session.clients.join(", ") || "Impilo session"}{session.current ? " · this session" : ""}</p><p className="text-xs text-muted-foreground">{session.ipAddress || "IP unavailable"}{session.lastActiveAt ? ` · active ${new Date(session.lastActiveAt).toLocaleString()}` : ""}</p></div></div>
                    {!session.current && <button onClick={() => revoke.mutate(session.id)} className="inline-flex items-center gap-1 text-xs font-medium text-danger hover:underline"><LogOut className="h-3.5 w-3.5" /> Revoke</button>}
                  </div>
                ))}
              </div>
            </section>
            {notice && <div className="flex items-center justify-between gap-3 rounded-xl border border-warning/40 bg-warning-soft p-4 text-sm"><span>{notice}</span><button onClick={() => stepUp.mutate()} className="font-semibold text-primary hover:underline">Verify again</button></div>}
            <p className="text-xs text-muted-foreground">Lost every factor? Contact support to open a governed recovery case. A requester cannot approve their own recovery.</p>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
