"use client";

/**
 * Trust & federation registry plane — entry to live admin surfaces for pods, keys, and consent.
 * Route: /registry/trust | guard: REGISTRY_ADMIN
 */

import Link from "next/link";
import { ArrowLeft, ArrowUpRight, Globe, KeyRound, HeartHandshake, ShieldCheck } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { RegistryPlaneContextBar } from "@/components/experience/RegistryPlaneContextBar";
import { PageShell } from "@/components/PageShell";

const TRUST_DESTINATIONS = [
  {
    id: "trust-console",
    title: "Trust console",
    description:
      "Live IATG governance queues via `/internal/v1/trust-console`: provider access, facility admin claims, org onboarding, assurance upgrades — with decisions.",
    href: "/registry-admin/trust-console",
    icon: ShieldCheck,
  },
  {
    id: "federation",
    title: "Federation & trust pods",
    description: "Currently unavailable: no typed Experience BFF contract exists for federation pod registry.",
    href: "/admin/federation",
    icon: Globe,
  },
  {
    id: "keys",
    title: "Cryptographic keys",
    description: "Currently unavailable: no typed Experience BFF contract exists for key inventory.",
    href: "/admin/keys",
    icon: KeyRound,
  },
  {
    id: "consent",
    title: "Consent governance",
    description: "Subject-scoped consent directives via `/internal/v1/admin/trust/consents`.",
    href: "/admin/consent",
    icon: HeartHandshake,
  },
] as const;

export default function RegistryTrustHubPage() {
  return (
    <AppLayout>
      <PageShell
        title="Trust & federation"
        subtitle="Registry-admin entry to operational trust surfaces backed by existing admin APIs"
        serviceSlug="tshepo"
      >
        <RegistryPlaneContextBar preferStore />

        <div className="mb-4">
          <Link
            href="/registry-admin"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="h-4 w-4" /> Back to registry administration
          </Link>
        </div>

        <p className="mb-4 text-sm text-muted-foreground">
          Only typed Experience BFF trust routes are treated as live. Federation and key inventory stay explicitly
          unavailable until canonical contracts exist; consent governance uses subject-scoped TSHEPO trust routes.
        </p>

        <div className="grid gap-4 md:grid-cols-3">
          {TRUST_DESTINATIONS.map((d) => {
            const Icon = d.icon;
            return (
              <Link
                key={d.id}
                href={`${d.href}?from=registry-admin`}
                className="group flex flex-col rounded-2xl border border-warning/35 bg-card p-5 shadow-sm transition hover:border-amber-400 hover:shadow-md"
              >
                <div className="flex items-start justify-between gap-2">
                  <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-amber-100 text-warning-foreground">
                    <Icon className="h-5 w-5" />
                  </div>
                  <ArrowUpRight className="h-4 w-4 text-muted-foreground group-hover:text-warning-foreground" />
                </div>
                <h3 className="mt-3 font-medium text-foreground group-hover:text-warning-foreground">{d.title}</h3>
                <p className="mt-1 text-sm text-muted-foreground">{d.description}</p>
              </Link>
            );
          })}
        </div>
      </PageShell>
    </AppLayout>
  );
}
