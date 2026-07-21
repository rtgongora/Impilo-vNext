"use client";

/**
 * Ruvimbo entry resolver (/ruvimbo).
 *
 * Ruvimbo is one product with several role-aware faces. This route resolves the caller
 * to the workspace(s) they are actually authorised for — citizen → My Ruvimbo, provider
 * → Ruvimbo Provider, payer → Ruvimbo Payer, admin → Administration / Performance. A user
 * with a single workspace is redirected straight in; a user with several sees a chooser.
 * We only ever surface workspaces the caller can access, so a selection never dead-ends in
 * an access-denied screen.
 */

import { useEffect, useMemo } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ShieldCheck, Stethoscope, Landmark, Settings2, BarChart3, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { matchesRequiredRole } from "@/lib/auth/role-groups";

interface RuvimboWorkspace {
  key: string;
  title: string;
  description: string;
  href: string;
  icon: React.ReactNode;
  /** Role-group key required; undefined = available to every authenticated person. */
  requiredRole?: string;
}

const WORKSPACES: RuvimboWorkspace[] = [
  {
    key: "member",
    title: "My Ruvimbo",
    description: "Manage your own and your household's coverage, benefits, claims and payments.",
    href: "/ruvimbo/member",
    icon: <ShieldCheck className="h-5 w-5" aria-hidden />,
  },
  {
    key: "provider",
    title: "Ruvimbo Provider",
    description: "Verify eligibility, manage authorisations, submit claims and recover payment.",
    href: "/ruvimbo/provider",
    icon: <Stethoscope className="h-5 w-5" aria-hidden />,
    requiredRole: "CLINICAL",
  },
  {
    key: "payer",
    title: "Ruvimbo Payer",
    description: "Administer membership, benefits, networks, adjudication, payments and appeals.",
    href: "/ruvimbo/payer",
    icon: <Landmark className="h-5 w-5" aria-hidden />,
    requiredRole: "PAYER_OPS",
  },
  {
    key: "administration",
    title: "Ruvimbo Administration",
    description: "Payer onboarding, claims-switch operations, standards, routing and platform governance.",
    href: "/ruvimbo/administration",
    icon: <Settings2 className="h-5 w-5" aria-hidden />,
    requiredRole: "ADMIN",
  },
  {
    key: "performance",
    title: "Ruvimbo Performance",
    description: "Monitor financing, claims, payer, provider, programme and switch performance.",
    href: "/ruvimbo/performance",
    icon: <BarChart3 className="h-5 w-5" aria-hidden />,
    requiredRole: "ADMIN",
  },
];

export default function RuvimboEntryPage() {
  const router = useRouter();
  const hasRole = useAuthStore((s) => s.hasRole);

  const available = useMemo(
    () => WORKSPACES.filter((w) => !w.requiredRole || matchesRequiredRole(hasRole, w.requiredRole)),
    [hasRole],
  );

  // Single workspace (the common citizen case) → go straight in.
  const soleHref = available.length === 1 ? available[0].href : null;
  useEffect(() => {
    if (soleHref) router.replace(soleHref);
  }, [soleHref, router]);

  if (soleHref) {
    return (
      <AppLayout>
        <PageShell title="Ruvimbo" serviceSlug="ruvimbo">
          <div className="flex items-center gap-2 py-12 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" aria-hidden /> Opening your Ruvimbo workspace…
          </div>
        </PageShell>
      </AppLayout>
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Ruvimbo"
        subtitle="Choose a workspace"
        serviceSlug="ruvimbo"
      >
        <p className="text-sm text-muted-foreground">
          You have access to more than one Ruvimbo workspace. Choose where you want to work.
        </p>
        <div className="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {available.map((w) => (
            <Link
              key={w.key}
              href={w.href}
              className="group block rounded-xl border border-border bg-card p-5 shadow-sm transition hover:border-violet-300 hover:shadow"
            >
              <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-violet-50 text-violet-700">
                {w.icon}
              </span>
              <h2 className="mt-3 text-sm font-semibold text-foreground group-hover:text-violet-800">
                {w.title}
              </h2>
              <p className="mt-1 text-xs text-muted-foreground">{w.description}</p>
            </Link>
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}
