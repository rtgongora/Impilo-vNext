"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ArrowLeft } from "lucide-react";
import { AdministrationGovernanceShell } from "./AdministrationGovernanceShell";
import { AccessRequestBoard } from "./AccessRequestBoard";
import { DomainGovernancePanel } from "./DomainGovernancePanel";
import { OrganisationRegistryPanel } from "./OrganisationRegistryPanel";
import { AuthorisedRepresentativesPanel } from "./AuthorisedRepresentativesPanel";
import { BulkImportPanel } from "./BulkImportPanel";
import { OrganisationDetailNav } from "./OrganisationDetailNav";
import { FriendlyBlockedState } from "./FriendlyBlockedState";
import { GovernanceActionResult } from "./GovernanceActionResult";
import { useSessionExperienceContract } from "@/hooks/useSessionExperienceContract";
import { useAdminGovernanceAudit, isGovernanceActionResult } from "@/hooks/useAdminGovernance";
import {
  ADMINISTRATION_SURFACES,
  tileEnabled,
  type AdministrationSurface,
} from "@/lib/administration-governance";

interface ScopedAdministrationSurfaceProps {
  surfaceId: string;
  backHref?: string;
}

export function ScopedAdministrationSurface({ surfaceId, backHref = "/work/administration-governance" }: ScopedAdministrationSurfaceProps) {
  const pathname = usePathname();
  const { contract } = useSessionExperienceContract();
  const surface: AdministrationSurface | undefined = ADMINISTRATION_SURFACES[surfaceId];

  if (!surface || !contract) {
    return (
      <AdministrationGovernanceShell title="Administration" requireGovernanceEntry={false}>
        <p className="text-sm text-muted-foreground">Loading…</p>
      </AdministrationGovernanceShell>
    );
  }

  const allowed = tileEnabled(contract, surface.requiredWorkspaces);

  return (
    <AdministrationGovernanceShell title={surface.title} subtitle={surface.subtitle} requireGovernanceEntry={false}>
      <div className="space-y-6">
        <Link href={backHref} className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="h-4 w-4" />
          Administration & Governance
        </Link>

        {!allowed ? (
          <FriendlyBlockedState state={contract.friendlyResolutionState ?? "policy_denied"} />
        ) : (
          <>
            <div className="rounded-xl border border-indigo-100 bg-info-soft/60 px-4 py-3 text-sm text-primary-hover">
              Actions on this page are organisation-scoped and OPA-enforced. Keycloak roles alone do not grant access.
            </div>
            {pathname.includes("/organisations/") && !pathname.endsWith("/new") ? <OrganisationDetailNav /> : null}
            <div className="grid gap-4 md:grid-cols-2">
              {surface.sections.map((section) =>
                section.href ? (
                  <Link
                    key={section.title}
                    href={section.href}
                    className="rounded-2xl border border-border bg-card p-5 shadow-sm transition hover:border-indigo-300"
                  >
                    <h4 className="font-medium text-foreground">{section.title}</h4>
                    <p className="mt-1 text-sm text-muted-foreground">{section.description}</p>
                  </Link>
                ) : (
                  <div key={section.title} className="rounded-2xl border border-border bg-card p-5 shadow-sm">
                    <h4 className="font-medium text-foreground">{section.title}</h4>
                    <p className="mt-1 text-sm text-muted-foreground">{section.description}</p>
                  </div>
                ),
              )}
            </div>
            {surfaceId === "access_requests" ? <AccessRequestBoard /> : null}
            {surfaceId === "audit" ? <AuditPanel /> : null}
            {surfaceId === "organisations" ? <OrganisationRegistryPanel /> : null}
            {pathname.includes("/users") ? <AuthorisedRepresentativesPanel /> : null}
            {pathname.includes("/data-uploads") ? <BulkImportPanel /> : null}
            <DomainGovernancePanel surfaceId={surfaceId} sourcePage={pathname} />
          </>
        )}
      </div>
    </AdministrationGovernanceShell>
  );
}

function AuditPanel() {
  const { data, isLoading } = useAdminGovernanceAudit();
  if (isLoading) return <p className="text-sm text-muted-foreground">Loading audit events…</p>;
  if (isGovernanceActionResult(data)) return <GovernanceActionResult result={data} />;
  return (
    <div className="rounded-xl border border-border bg-card p-4 text-sm text-foreground">
      {(data ?? []).length === 0 ? (
        <p>No audit events returned for your scope yet.</p>
      ) : (
        <ul className="space-y-2">
          {(data ?? []).map((event, index) => (
            <li key={`${event.eventType}-${index}`} className="rounded-lg border border-border px-3 py-2">
              <span className="font-medium">{event.eventType}</span>
              {event.occurredAt ? <span className="text-muted-foreground"> · {event.occurredAt}</span> : null}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
