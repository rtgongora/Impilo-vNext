"use client";

import { useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { AlertTriangle, BookOpen, CheckCircle2, ChevronLeft, Loader2, ShieldCheck, Tag } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useMarketplaceCapability,
  useRequestActivation,
} from "@/hooks/queries/useHealthOsLauncher";

export default function CapabilityDetailPage() {
  const params = useParams<{ itemCode: string }>();
  const itemCode = decodeURIComponent(params?.itemCode ?? "");
  const capabilityQuery = useMarketplaceCapability(itemCode);
  const requestActivation = useRequestActivation();

  const [purpose, setPurpose] = useState("TREATMENT");
  const [justification, setJustification] = useState("");
  const [facilityId, setFacilityId] = useState("");
  const [success, setSuccess] = useState<string | null>(null);

  const item = capabilityQuery.data;

  async function handleRequest() {
    if (!item || !justification.trim()) return;
    try {
      const r = await requestActivation.mutateAsync({
        itemId: item.id,
        purposeOfUse: purpose,
        justification: justification.trim(),
        facilityId: facilityId.trim() || undefined,
      });
      setSuccess(`Request ${r.id} submitted for review.`);
      setJustification("");
    } catch (e) {
      setSuccess(null);
    }
  }

  return (
    <AppLayout>
      <PageShell
        title={item?.name ?? "Capability"}
        subtitle={item?.description ?? "Health OS Capability Marketplace"}
      >
        <div className="space-y-6">
          <Link
            href="/marketplace/apps"
            className="inline-flex items-center gap-1 text-sm text-primary-hover hover:underline"
          >
            <ChevronLeft className="h-4 w-4" /> Back to marketplace
          </Link>

          {capabilityQuery.isLoading ? (
            <div className="flex items-center gap-2 rounded-2xl border border-border bg-card p-6 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" /> Loading capability…
            </div>
          ) : capabilityQuery.isError || !item ? (
            <div className="flex items-start gap-2 rounded-2xl border border-warning/35 bg-warning-soft p-4 text-sm text-warning-foreground">
              <AlertTriangle className="mt-0.5 h-4 w-4" />
              Couldn’t load this capability. It may have been deprecated or you may not have access.
            </div>
          ) : (
            <>
              <section className="rounded-2xl border border-border bg-card p-5 shadow-sm">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="text-[11px] font-semibold uppercase tracking-wider text-primary-hover">
                      {item.type} · {item.category}
                    </div>
                    <h2 className="mt-1 text-xl font-semibold text-foreground">{item.name}</h2>
                    <p className="mt-1 max-w-3xl text-sm text-muted-foreground">{item.description}</p>
                  </div>
                  <div className="flex flex-col items-end gap-1">
                    <ApprovalBadge value={item.approvalStatus} />
                    <span className="text-xs text-muted-foreground">v{item.version}</span>
                  </div>
                </div>
                <div className="mt-4 grid gap-3 text-sm text-muted-foreground md:grid-cols-2">
                  <Detail label="Publisher" value={item.publisherName} />
                  <Detail label="Security" value={item.securityClassification} />
                  <Detail label="Data protection" value={item.dataProtectionClassification} />
                  <Detail label="Clinical safety" value={item.clinicalSafetyClassification ?? "—"} />
                  <Detail label="Environments" value={item.supportedEnvironments.join(", ")} />
                  <Detail label="Visibility" value={item.visibility} />
                </div>

                {item.documentationUrl ? (
                  <a
                    href={item.documentationUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="mt-4 inline-flex items-center gap-1 text-sm font-medium text-primary-hover hover:underline"
                  >
                    <BookOpen className="h-4 w-4" /> Documentation
                  </a>
                ) : null}
              </section>

              <section className="grid gap-4 lg:grid-cols-2">
                <Permissions title="Required scopes"   items={item.requiredScopes} icon={ShieldCheck} />
                <Permissions title="Required APIs"     items={item.requiredApis}   icon={Tag} />
                <Permissions title="Required events"   items={item.requiredEvents} icon={Tag} />
                <Permissions title="Data categories"   items={item.requiredDataCategories} icon={Tag} />
              </section>

              <section className="rounded-2xl border border-border bg-card p-5 shadow-sm">
                <h3 className="text-base font-semibold text-foreground">Request activation</h3>
                <p className="mt-1 text-sm text-muted-foreground">
                  Activation is governed. A reviewer will validate scope, data access, and clinical safety before
                  installing this capability for your tenant/facility.
                </p>
                <div className="mt-4 grid gap-3 md:grid-cols-2">
                  <label className="text-sm">
                    <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Purpose of use</span>
                    <select
                      value={purpose}
                      onChange={(e) => setPurpose(e.target.value)}
                      className="mt-1 w-full rounded-lg border border-border px-3 py-2"
                    >
                      <option value="TREATMENT">Treatment</option>
                      <option value="OPERATIONS">Operations</option>
                      <option value="QUALITY_IMPROVEMENT">Quality improvement</option>
                      <option value="PUBLIC_HEALTH">Public health</option>
                      <option value="RESEARCH">Research</option>
                      <option value="ADMINISTRATIVE">Administrative</option>
                    </select>
                  </label>
                  <label className="text-sm">
                    <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Facility (optional)</span>
                    <input
                      value={facilityId}
                      onChange={(e) => setFacilityId(e.target.value)}
                      className="mt-1 w-full rounded-lg border border-border px-3 py-2"
                      placeholder="e.g. fac-mpilo-001"
                    />
                  </label>
                </div>
                <label className="mt-3 block text-sm">
                  <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Justification</span>
                  <textarea
                    value={justification}
                    onChange={(e) => setJustification(e.target.value)}
                    rows={4}
                    className="mt-1 w-full rounded-lg border border-border px-3 py-2"
                    placeholder="Why does your facility / programme need this capability?"
                  />
                </label>
                <div className="mt-3 flex items-center gap-3">
                  <button
                    type="button"
                    disabled={!justification.trim() || requestActivation.isPending}
                    onClick={handleRequest}
                    className="inline-flex items-center gap-2 rounded-xl bg-primary-hover px-4 py-2 text-sm font-semibold text-white disabled:cursor-not-allowed disabled:opacity-60 hover:bg-impilo-700"
                  >
                    {requestActivation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                    Submit activation request
                  </button>
                  {success ? (
                    <div className="inline-flex items-center gap-1 text-sm text-primary-hover">
                      <CheckCircle2 className="h-4 w-4" /> {success}
                    </div>
                  ) : null}
                </div>
              </section>
            </>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-border bg-background px-3 py-2">
      <div className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">{label}</div>
      <div className="mt-0.5 text-sm font-medium text-foreground">{value || "—"}</div>
    </div>
  );
}

function Permissions({
  title,
  items,
  icon: Icon,
}: {
  title: string;
  items: string[];
  icon: React.ComponentType<{ className?: string }>;
}) {
  return (
    <div className="rounded-2xl border border-border bg-card p-4 shadow-sm">
      <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
        <Icon className="h-4 w-4 text-primary" /> {title}
      </div>
      {items.length === 0 ? (
        <p className="mt-2 text-xs text-muted-foreground">None declared.</p>
      ) : (
        <ul className="mt-2 flex flex-wrap gap-1">
          {items.map((s) => (
            <li
              key={s}
              className="rounded-full bg-neutral-100 px-2 py-0.5 text-[11px] text-foreground"
            >
              {s}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function ApprovalBadge({ value }: { value: string }) {
  const tone: Record<string, string> = {
    APPROVED: "bg-success-soft text-primary-hover",
    PENDING_REVIEW: "bg-warning-soft text-warning-foreground",
    REJECTED: "bg-danger-soft text-danger",
    DEPRECATED: "bg-neutral-100 text-muted-foreground",
    SUSPENDED: "bg-danger-soft text-danger",
  };
  return (
    <span
      className={`rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${tone[value] ?? "bg-neutral-100 text-foreground"}`}
    >
      {value}
    </span>
  );
}
