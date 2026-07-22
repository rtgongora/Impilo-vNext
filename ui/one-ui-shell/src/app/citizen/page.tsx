"use client";

import Link from "next/link";
import { IdentityAssuranceBanner } from "@/components/citizen/IdentityAssuranceBanner";

const LINKS: { href: string; label: string; note: string }[] = [
  { href: "/citizen/my-care", label: "My Care", note: "Your health at a glance and how to carry on with your care" },
  { href: "/citizen/wallet", label: "My Health Wallet", note: "Your health life in one place — identity, records, care, consent, payments" },
  { href: "/citizen/health-id/qr", label: "My Impilo ID QR", note: "VITO portal contract via gateway" },
  { href: "/citizen/health-id/request", label: "Request Impilo ID", note: "POST /api/v1/portal/id/request" },
  { href: "/citizen/id-recovery", label: "ID recovery", note: "Step-up + VITO recovery" },
  { href: "/citizen/delegated-pickup", label: "Delegated pickup", note: "Create / redeem pickup" },
  { href: "/verify/credential", label: "Verify a credential", note: "GET /v1/public/verify/{token} — set NEXT_PUBLIC_CREDENTIAL_VERIFY_PUBLIC_URL" },
  { href: "/share/claim", label: "Claim shared documents", note: "Share-slip public API (set NEXT_PUBLIC_SHARE_SLIP_PUBLIC_URL)" },
];

export default function CitizenHubPage() {
  return (
    <div className="space-y-6">
      {/* Health OS §10–§11: Progressive identity assurance — guided journey */}
      <IdentityAssuranceBanner />

      <div className="rounded-lg border border-border bg-background p-4 text-xs text-foreground">
        <p className="font-medium text-foreground mb-1">Life-context hub</p>
        <p>
          Citizen routes are auth-guarded; verify and claim are public. Sidebar/spotlight entries follow the route registry
          maintained by integration.
        </p>
      </div>

      <ul className="grid gap-3 sm:grid-cols-2">
        {LINKS.map((l) => (
          <li key={l.href}>
            <Link
              href={l.href}
              className="block rounded-lg border border-border bg-card p-4 hover:border-primary/25 hover:shadow-sm transition-all"
            >
              <span className="font-medium text-foreground">{l.label}</span>
              <p className="text-xs text-muted-foreground mt-1">{l.note}</p>
            </Link>
          </li>
        ))}
      </ul>

      <div className="rounded-lg border border-dashed border-border p-4 text-xs text-muted-foreground">
        <p className="font-medium text-foreground mb-1">Experience self-service parity</p>
        <p>
          The former self-service flows now live in the Experience shell: verification at{" "}
          <code className="text-[10px]">/verify/credential</code>, claim at{" "}
          <code className="text-[10px]">/share/claim</code>, documents at{" "}
          <code className="text-[10px]">/home/documents</code>, and credentials at{" "}
          <code className="text-[10px]">/home/credentials</code>.
        </p>
      </div>
    </div>
  );
}
