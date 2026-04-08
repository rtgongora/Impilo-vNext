"use client";

/**
 * High-trust registry plane banner when navigating from registry administration
 * or when operational context is registry_admin with a remembered subtype.
 */

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Shield } from "lucide-react";
import { useOperationalContextStore } from "@/hooks/useOperationalContextStore";
import { OPERATIONAL_MODE_DEF, type RegistryAdminSubtype } from "@/lib/operational-context";

const SUBTYPE_LABEL: Record<RegistryAdminSubtype, string> = {
  client_registry: "Client registry (MPI)",
  provider_registry: "Provider registry",
  facility_registry: "Facility registry",
  terminology_admin: "Terminology administration",
  trust_admin: "Trust & federation",
};

interface RegistryPlaneContextBarProps {
  /** When true, show bar if store says registry_admin even without ?from= */
  preferStore?: boolean;
}

export function RegistryPlaneContextBar({ preferStore = false }: RegistryPlaneContextBarProps) {
  const searchParams = useSearchParams();
  const fromRegistryAdmin = searchParams.get("from") === "registry-admin";
  const operationalMode = useOperationalContextStore((s) => s.operationalMode);
  const subtype = useOperationalContextStore((s) => s.registryAdminSubtype);

  const activePlane =
    fromRegistryAdmin || (preferStore && operationalMode === "registry_admin");

  if (!activePlane) return null;

  const modeDef = OPERATIONAL_MODE_DEF.registry_admin;
  const subLabel = subtype ? SUBTYPE_LABEL[subtype] : null;

  return (
    <div className="mb-4 rounded-xl border border-amber-300/90 bg-gradient-to-r from-amber-50 to-amber-100/40 px-4 py-3 text-sm text-amber-950 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-start gap-2">
          <Shield className="mt-0.5 h-4 w-4 shrink-0 text-amber-800" aria-hidden />
          <div>
            <p className="font-semibold text-amber-950">{modeDef.label}</p>
            <p className="text-xs text-amber-900/85">
              {subLabel ? (
                <>
                  Active sub-plane: <span className="font-medium">{subLabel}</span>. Actions may be audited under
                  elevated trust rules.
                </>
              ) : (
                <>
                  You entered from registry administration. This is high-trust registry work — not facility shift
                  operations.
                </>
              )}
            </p>
          </div>
        </div>
        <Link
          href="/registry-admin"
          className="shrink-0 rounded-lg border border-amber-400/80 bg-white/80 px-3 py-1.5 text-xs font-medium text-amber-950 hover:bg-white"
        >
          Registry admin home
        </Link>
      </div>
    </div>
  );
}
