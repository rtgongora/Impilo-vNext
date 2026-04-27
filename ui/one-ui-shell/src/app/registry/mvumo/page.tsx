"use client";

import Link from "next/link";
import { FileCheck, Shield } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

/**
 * Mvumo — product surface for the national digital consent orchestration service.
 * APIs live on mvumo-service; trust-layer FHIR/evaluation remains on tshepo-consent-service.
 */
export default function MvumoRegistryPage() {
  return (
    <AppLayout>
      <PageShell
        title="Mvumo"
        subtitle="Adaptive digital consent — not a checkbox, not PDF-only, not a single signature channel"
        icon={<Shield className="h-6 w-6 text-impilo-600" />}
      >
        <div className="prose prose-sm max-w-none text-gray-700 space-y-4">
          <p>
            <strong>Mvumo</strong> orchestrates consent <em>requests</em>, <em>templates</em>,{" "}
            <em>remote sessions</em>, and <em>adaptive assurance</em> across portals, tokens, OTP, PIN, USSD,
            assisted facility capture, offline sync, paper-to-digital proof, witnessed flows, and break-glass
            acknowledgement — as policy allows.
          </p>
          <p>
            Enforcement and FHIR <code className="rounded bg-gray-100 px-1">Consent</code> evaluation remain in{" "}
            <strong>tshepo-consent-service</strong>. See repository docs:{" "}
            <code className="rounded bg-gray-100 px-1">docs/architecture/mvumo-consent-architecture.md</code>.
          </p>
          <div className="flex flex-wrap gap-3 not-prose">
            <Link
              href="https://github.com/rtgongora/Impilo-vNext/blob/claude/staging-ux-orchestration-remediation-Yypyl/docs/architecture/mvumo-consent-architecture.md"
              className="inline-flex items-center gap-2 rounded-lg border border-impilo-200 bg-impilo-50 px-4 py-2 text-sm font-medium text-impilo-800 hover:bg-impilo-100"
            >
              <FileCheck className="h-4 w-4" /> Architecture doc
            </Link>
            <Link
              href="https://github.com/rtgongora/Impilo-vNext/blob/claude/staging-ux-orchestration-remediation-Yypyl/docs/audits/mvumo-consent-current-state-audit.md"
              className="inline-flex items-center gap-2 rounded-lg border border-gray-200 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
            >
              Current-state audit
            </Link>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
