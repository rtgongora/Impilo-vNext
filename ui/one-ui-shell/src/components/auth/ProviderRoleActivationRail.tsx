"use client";

import Link from "next/link";
import { AlertCircle, Loader2, ShieldCheck, Stethoscope } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useLinkedIds } from "@/hooks/queries/useLinkedIds";

interface ProviderRoleActivationRailProps {
  compact?: boolean;
  returnTo?: string;
}

export function ProviderRoleActivationRail({
  compact = false,
  returnTo = "/clinical",
}: ProviderRoleActivationRailProps) {
  const { user } = useAuthStore();
  const { data, isLoading, isError } = useLinkedIds();

  const linked = data?.data?.attributes;
  const hasProvider = !!linked?.providerId;
  const isActivated = !!user?.providerActivated && !!user?.providerId;

  if (isActivated) {
    return (
      <section
        className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3"
        data-testid="provider-role-activation-rail"
      >
        <div className="flex items-start gap-2 text-sm text-emerald-900">
          <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-emerald-600" />
          <div>
            <p className="font-medium">Provider role active</p>
            <p className="text-emerald-800">
              Practising as <span className="font-mono">{user?.providerId}</span> under Health OS §6.
            </p>
          </div>
        </div>
      </section>
    );
  }

  if (isLoading) {
    return (
      <section
        className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3"
        data-testid="provider-role-activation-rail"
        aria-busy="true"
      >
        <div className="flex items-center gap-2 text-sm text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" />
          Resolving linked professional identities…
        </div>
      </section>
    );
  }

  if (isError) {
    return (
      <section
        className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3"
        data-testid="provider-role-activation-rail"
      >
        <div className="flex items-start gap-2 text-sm text-amber-900">
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
          <p>Linked-ID lookup unavailable. Retry login or continue as citizen until BFF is reachable.</p>
        </div>
      </section>
    );
  }

  if (!hasProvider) {
    return (
      <section
        className={`rounded-xl border border-slate-200 bg-white px-4 py-3 ${compact ? "" : "shadow-sm"}`}
        data-testid="provider-role-activation-rail"
      >
        <div className="flex items-start gap-2 text-sm text-slate-700">
          <Stethoscope className="mt-0.5 h-4 w-4 shrink-0 text-slate-500" />
          <div>
            <p className="font-medium text-slate-900">Citizen session</p>
            <p>No active Provider ID is linked to this Health ID. Wellness and citizen journeys remain available.</p>
          </div>
        </div>
      </section>
    );
  }

  const activateHref = `/provider/activate?returnTo=${encodeURIComponent(returnTo)}`;

  return (
    <section
      className={`rounded-xl border border-impilo-200 bg-impilo-50 px-4 py-3 ${compact ? "" : "shadow-sm"}`}
      data-testid="provider-role-activation-rail"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex items-start gap-2 text-sm text-impilo-900">
          <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-impilo-600" />
          <div>
            <p className="font-medium">Provider capacity detected — activation required</p>
            <p>
              Linked Provider ID <span className="font-mono">{linked?.providerId}</span> must be activated before
              regulated clinical work. Sign in as a person; practice only under an activated Provider ID.
            </p>
          </div>
        </div>
        <Link
          href={activateHref}
          className="inline-flex shrink-0 items-center rounded-lg bg-impilo-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-impilo-700"
        >
          Activate provider role
        </Link>
      </div>
    </section>
  );
}
