"use client";

import Link from "next/link";
import { IdentityAssuranceBanner } from "@/components/citizen/IdentityAssuranceBanner";

const LINKS: { href: string; label: string; note: string }[] = [
  { href: "/citizen/health-id/qr", label: "My Health ID QR", note: "VITO portal contract via gateway" },
  { href: "/citizen/health-id/request", label: "Request Health ID", note: "POST /api/v1/portal/id/request" },
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

      <div className="rounded-lg border border-gray-200 bg-gray-50 p-4 text-xs text-gray-700">
        <p className="font-medium text-gray-900 mb-1">Life-context hub</p>
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
              className="block rounded-lg border border-gray-200 bg-white p-4 hover:border-blue-300 hover:shadow-sm transition-all"
            >
              <span className="font-medium text-gray-900">{l.label}</span>
              <p className="text-xs text-gray-500 mt-1">{l.note}</p>
            </Link>
          </li>
        ))}
      </ul>

      <div className="rounded-lg border border-dashed border-gray-300 p-4 text-xs text-gray-600">
        <p className="font-medium text-gray-800 mb-1">Still sidecar-only (backlog)</p>
        <ul className="list-disc pl-4 space-y-1">
          <li>
            <code className="text-[10px]">ui/self-service</code> my-documents / my-credentials — still blocked (no Experience
            contract in this lane).
          </li>
        </ul>
      </div>
    </div>
  );
}
