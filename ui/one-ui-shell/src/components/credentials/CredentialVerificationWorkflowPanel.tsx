"use client";

import Link from "next/link";
import { BadgeCheck, ExternalLink, QrCode } from "lucide-react";
import { usePersonalCredentials } from "@/hooks/queries/usePersonalCredentials";

export function CredentialVerificationWorkflowPanel() {
  const { data, isLoading } = usePersonalCredentials();
  const credentials = data?.data ?? [];

  return (
    <section
      className="rounded-xl border border-border bg-card p-4 shadow-sm"
      data-testid="credential-verification-workflow-panel"
    >
      <div className="flex items-start gap-3">
        <BadgeCheck className="mt-0.5 h-5 w-5 shrink-0 text-primary" />
        <div className="space-y-3 text-sm text-foreground">
          <div>
            <p className="font-medium text-foreground">Public credential verification workflow</p>
            <p className="mt-1">
              Third parties verify certificates using the sovereign public verifier. Vault credentials below expose
              verification URLs without leaking PII through the Experience shell.
            </p>
          </div>
          <ol className="list-decimal space-y-1 pl-5 text-xs text-muted-foreground">
            <li>Issuer publishes credential to your vault (credential-service).</li>
            <li>Verifier scans QR or opens token link.</li>
            <li>
              <code className="text-[10px]">GET /v1/public/verify/{"{token}"}</code> returns VALID, EXPIRED, or REVOKED.
            </li>
          </ol>
          {isLoading ? (
            <p className="text-xs text-muted-foreground">Loading vault credentials…</p>
          ) : credentials.length > 0 ? (
            <ul className="space-y-2 text-xs">
              {credentials.slice(0, 5).map((cred) => (
                <li key={cred.credentialId} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-border px-3 py-2">
                  <span className="font-medium text-foreground">{cred.title}</span>
                  <span className="text-muted-foreground">{cred.status}</span>
                  {cred.verificationUrl ? (
                    <a href={cred.verificationUrl} target="_blank" rel="noopener noreferrer" className="text-primary hover:underline">
                      Verify
                    </a>
                  ) : (
                    <Link href="/verify/credential" className="text-primary hover:underline">
                      Open verifier
                    </Link>
                  )}
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-xs text-muted-foreground">No personal credentials in vault yet.</p>
          )}
          <div className="flex flex-wrap gap-2">
            <Link
              href="/verify/credential"
              className="inline-flex items-center gap-1.5 rounded-lg bg-primary-hover px-3 py-1.5 text-xs font-medium text-white hover:bg-impilo-700"
            >
              <QrCode className="h-3.5 w-3.5" />
              Open verifier
            </Link>
            <a
              href="https://docs.impilo.health/credentials/verify"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-foreground hover:bg-background"
            >
              <ExternalLink className="h-3.5 w-3.5" />
              Verification doctrine
            </a>
          </div>
        </div>
      </div>
    </section>
  );
}
