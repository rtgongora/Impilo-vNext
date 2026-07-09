"use client";

/**
 * Registry Administration landing — high-trust sovereign registry plane.
 * Route: /registry-admin | operational context: registry_admin
 *
 * Differs from /registry (browse hub) and generic /admin: explicit governance framing.
 */

import { useEffect } from "react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import {
  ArrowLeft,
  ArrowUpRight,
  BookOpen,
  Building2,
  Route,
  Shield,
  UserCheck,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { RegistryAdministrationOrchestrationRail } from "@/components/platform/RegistryAdministrationOrchestrationRail";
import { PlaneTrustBanner } from "@/components/experience/PlaneTrustBanner";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useOperationalContextStore } from "@/hooks/useOperationalContextStore";
import { REGISTRY_PLANE_CARDS } from "@/lib/plane-landings";
import { isRegistryAdminSubtype, principalHasRegistryAdminPlane } from "@/lib/operational-context";

const SUBTYPE_ICONS = {
  registry_intake: Route,
  client_registry: Shield,
  provider_registry: UserCheck,
  facility_registry: Building2,
  terminology_admin: BookOpen,
  trust_admin: Shield,
} as const;

export default function RegistryAdminLandingPage() {
  const searchParams = useSearchParams();
  const rehydrate = useOperationalContextStore((s) => s.rehydrateFromSession);

  useEffect(() => {
    const user = useAuthStore.getState().user;
    if (principalHasRegistryAdminPlane(user)) {
      useOperationalContextStore.getState().setOperationalMode("registry_admin");
    }
  }, []);

  useEffect(() => {
    const raw = searchParams.get("subtype");
    if (raw && isRegistryAdminSubtype(raw)) {
      useOperationalContextStore.getState().setRegistryAdminSubtype(raw);
    }
  }, [searchParams]);

  return (
    <AppLayout>
      <PageShell
        title="Registry administration"
        subtitle="Sovereign registry governance — not facility shift work"
      >
        <div className="space-y-6">
          <RegistryAdministrationOrchestrationRail />
          <Link
            href="/home"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="h-4 w-4" /> Back to home
          </Link>

          <PlaneTrustBanner
            variant="registry"
            eyebrow="High-trust plane"
            title="You are in the registry governance context"
          >
            <p>
              Use this entry when changing national or enterprise registries, terminology, or trust fabric.
              Browse-oriented registry tools remain on{" "}
              <Link href="/registry" className="font-medium underline-offset-2 hover:underline">
                Registry hub
              </Link>{" "}
              — this page is for <strong>administrative</strong> registry operations with elevated expectations.
            </p>
          </PlaneTrustBanner>

          <Link
            href="/registry-admin/trust-console"
            className="group flex items-center justify-between gap-4 rounded-2xl border border-warning/35 bg-card p-5 shadow-sm transition hover:border-amber-400 hover:shadow-md"
          >
            <div className="flex items-start gap-3">
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-amber-100 text-warning-foreground">
                <Shield className="h-5 w-5" />
              </div>
              <div>
                <h4 className="font-medium text-foreground group-hover:text-warning-foreground">Trust console</h4>
                <p className="mt-1 text-sm text-muted-foreground">
                  Unified IATG governance queues: pending provider access requests, facility admin claims,
                  organisation onboarding, assurance upgrades — review and decide in one place.
                </p>
              </div>
            </div>
            <ArrowUpRight className="h-4 w-4 shrink-0 text-muted-foreground group-hover:text-warning-foreground" />
          </Link>

          <div>
            <h3 className="text-sm font-semibold text-foreground">Registry sub-planes</h3>
            <p className="mt-1 text-xs text-muted-foreground">
              Pick a sub-plane to focus tooling; your selection is remembered for this session (
              <button
                type="button"
                className="text-primary hover:underline"
                onClick={() => {
                  useOperationalContextStore.getState().setRegistryAdminSubtype(null);
                  rehydrate();
                }}
              >
                clear subtype
              </button>
              ).
            </p>
            <div className="mt-4 grid gap-4 md:grid-cols-2">
              {REGISTRY_PLANE_CARDS.map((card) => {
                const Icon = SUBTYPE_ICONS[card.subtype];
                return (
                  <Link
                    key={card.subtype}
                    href={`${card.href}${card.href.includes("?") ? "&" : "?"}from=registry-admin`}
                    onClick={() =>
                      useOperationalContextStore.getState().setRegistryAdminSubtype(card.subtype)
                    }
                    className="group flex flex-col rounded-2xl border border-warning/35/80 bg-card p-5 shadow-sm transition hover:border-amber-400 hover:shadow-md"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="flex items-start gap-3">
                        <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-amber-100 text-warning-foreground">
                          <Icon className="h-5 w-5" />
                        </div>
                        <div>
                          <h4 className="font-medium text-foreground group-hover:text-warning-foreground">{card.title}</h4>
                          <p className="mt-1 text-sm text-muted-foreground">{card.description}</p>
                        </div>
                      </div>
                      <ArrowUpRight className="h-4 w-4 shrink-0 text-muted-foreground group-hover:text-warning-foreground" />
                    </div>
                  </Link>
                );
              })}
            </div>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
