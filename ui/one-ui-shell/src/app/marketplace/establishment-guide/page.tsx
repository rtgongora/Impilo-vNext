"use client";

/**
 * Set up your practice — establishment guide.
 * Route: /marketplace/establishment-guide (auth, MINIMAL friction)
 *
 * Buyer-toned guide for prospective practice founders: pick the facility type you
 * want to establish, see every HPA inspection requirement it will be assessed
 * against, and jump straight to marketplace sellers for the requirements that map
 * to a sourcing category. Read-only composition over the HPA checklist catalogue
 * (useHpaRegulatory) and the requirement-sourcing bridge (useRequirementSourcing).
 */

import { useMemo, useState } from "react";
import Link from "next/link";
import { ArrowRight, Building2, Loader2, ShoppingBag, ShieldAlert } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";
import {
  useInspectionCompositions,
  useInspectionModules,
  type ChecklistItemView,
  type InspectionModuleView,
} from "@/hooks/queries/useHpaRegulatory";
import {
  useResolveRequirementSourcing,
  type RequirementSourcingResolution,
} from "@/hooks/queries/useRequirementSourcing";

const OBLIGATION_BADGE: Record<string, string> = {
  MANDATORY: "bg-red-50 text-red-700 border-red-200",
  RECOMMENDED: "bg-sky-50 text-sky-700 border-sky-200",
  OPTIONAL: "bg-muted text-muted-foreground border-border",
  WHERE_APPLICABLE: "bg-amber-50 text-amber-700 border-amber-200",
  WHERE_POSSIBLE: "bg-amber-50 text-amber-700 border-amber-200",
};

/** Section rows with COMPONENT items nested under their PARENT (by shared group). */
interface SectionRow {
  item: ChecklistItemView;
  children: ChecklistItemView[];
}

function buildSectionRows(items: ChecklistItemView[]): SectionRow[] {
  const rows: SectionRow[] = [];
  const parentByGroup = new Map<string, SectionRow>();
  for (const item of items) {
    if (item.group && item.groupRole === "PARENT") {
      const row: SectionRow = { item, children: [] };
      parentByGroup.set(item.group, row);
      rows.push(row);
    } else if (item.group && item.groupRole === "COMPONENT" && parentByGroup.has(item.group)) {
      parentByGroup.get(item.group)!.children.push(item);
    } else {
      rows.push({ item, children: [] });
    }
  }
  return rows;
}

function SupplierChip({ resolution }: { resolution: RequirementSourcingResolution }) {
  return (
    <span className="inline-flex flex-wrap items-center gap-2">
      <Link
        href={`/marketplace/store?category=${encodeURIComponent(resolution.categoryCode)}`}
        className="inline-flex items-center gap-1 rounded-full border border-primary/30 bg-primary/5 px-2 py-0.5 text-[11px] font-medium text-primary hover:bg-primary/10"
      >
        <ShoppingBag className="h-3 w-3" />
        Find suppliers · {resolution.publishedListingCount} listing
        {resolution.publishedListingCount === 1 ? "" : "s"}
      </Link>
      {resolution.restricted && (
        <span className="inline-flex items-center gap-1 text-[10px] text-amber-700">
          <ShieldAlert className="h-3 w-3" /> licensed suppliers only
        </span>
      )}
    </span>
  );
}

function RequirementRow({
  item,
  resolutions,
  indent,
}: {
  item: ChecklistItemView;
  resolutions: Record<string, RequirementSourcingResolution>;
  indent?: boolean;
}) {
  const obligation = item.obligation ?? null;
  const badge = obligation ? (OBLIGATION_BADGE[obligation] ?? "bg-muted text-muted-foreground border-border") : null;
  const resolution = resolutions[item.code];
  return (
    <div className={`rounded-md border border-border/60 bg-background p-3 ${indent ? "ml-6" : ""}`}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <p className="min-w-0 flex-1 text-sm text-foreground">{item.requirement ?? item.code}</p>
        {badge && obligation && (
          <span className={`shrink-0 rounded-full border px-2 py-0.5 text-[10px] font-medium ${badge}`}>
            {obligation.replace(/_/g, " ")}
          </span>
        )}
      </div>
      <div className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-muted-foreground">
        <span className="font-mono text-[10px]">{item.code}</span>
        {item.quantity && <span>Quantity: {item.quantity}</span>}
      </div>
      {resolution && (
        <div className="mt-2">
          <SupplierChip resolution={resolution} />
        </div>
      )}
    </div>
  );
}

function ModuleRequirements({
  module,
  resolutions,
}: {
  module: InspectionModuleView;
  resolutions: Record<string, RequirementSourcingResolution>;
}) {
  const items = module.items ?? [];
  const sections = useMemo(() => {
    const bySection = new Map<string, ChecklistItemView[]>();
    for (const item of items) {
      const section = item.section?.trim() || "General";
      const bucket = bySection.get(section);
      if (bucket) bucket.push(item);
      else bySection.set(section, [item]);
    }
    return [...bySection.entries()];
  }, [items]);

  return (
    <section className="rounded-2xl border border-border bg-card p-5 shadow-sm">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="text-base font-semibold text-foreground">{module.title}</h3>
        <span className="text-xs text-muted-foreground">
          {items.length} requirement{items.length === 1 ? "" : "s"}
          {module.sourceHeading ? ` · ${module.sourceHeading}` : ""}
        </span>
      </div>
      <div className="mt-3 space-y-4 overflow-x-auto">
        {sections.map(([section, sectionItems]) => (
          <div key={section}>
            <h4 className="mb-2 text-xs font-medium uppercase tracking-wide text-muted-foreground">{section}</h4>
            <ul className="space-y-2">
              {buildSectionRows(sectionItems).map((row) => (
                <li key={row.item.code} className="space-y-2">
                  <RequirementRow item={row.item} resolutions={resolutions} />
                  {row.children.map((child) => (
                    <RequirementRow key={child.code} item={child} resolutions={resolutions} indent />
                  ))}
                </li>
              ))}
            </ul>
          </div>
        ))}
        {items.length === 0 && (
          <p className="text-sm text-muted-foreground">This module has no published requirements yet.</p>
        )}
      </div>
    </section>
  );
}

export default function EstablishmentGuidePage() {
  const compositionsQuery = useInspectionCompositions();
  const modulesQuery = useInspectionModules();
  const [selectedCode, setSelectedCode] = useState<string | null>(null);

  const compositions = useMemo(
    () => (compositionsQuery.data?.data ?? []).filter((c) => c.status === "ACTIVE"),
    [compositionsQuery.data],
  );
  const selected = compositions.find((c) => c.code === selectedCode) ?? null;

  const modules = useMemo(() => {
    if (!selected) return [];
    const byCode = new Map((modulesQuery.data?.data ?? []).map((m) => [m.code, m]));
    return selected.moduleCodes.map((code) => byCode.get(code)).filter((m): m is InspectionModuleView => !!m);
  }, [selected, modulesQuery.data]);

  const allItemCodes = useMemo(
    () => modules.flatMap((m) => (m.items ?? []).map((i) => i.code)),
    [modules],
  );
  const resolveQuery = useResolveRequirementSourcing(allItemCodes);
  const resolutions = resolveQuery.data?.data.resolutions ?? {};

  return (
    <AppLayout>
      <PageShell
        title="Set up your practice"
        subtitle="Thinking of opening a surgery, clinic or other health facility? See exactly what inspectors will look for — and find marketplace sellers for the things you need to put in place."
        serviceSlug="msika"
      >
        <div className="space-y-6">
          <NompiloContextualGuidance routePath="/marketplace/establishment-guide" />

          <section className="rounded-2xl border border-border bg-card p-5 shadow-sm">
            <h2 className="flex items-center gap-2 text-lg font-semibold text-foreground">
              <Building2 className="h-5 w-5 text-primary" /> 1. What kind of facility are you setting up?
            </h2>
            <p className="mt-1 text-sm text-muted-foreground">
              Each facility type has its own HPA inspection checklist. Pick one to see everything you will need.
            </p>
            {compositionsQuery.isLoading && (
              <p className="mt-4 flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" /> Loading facility types…
              </p>
            )}
            {compositionsQuery.isError && (
              <p className="mt-4 text-sm text-red-600">Could not load facility types right now.</p>
            )}
            {!compositionsQuery.isLoading && !compositionsQuery.isError && compositions.length === 0 && (
              <p className="mt-4 text-sm text-muted-foreground">No published facility checklists yet.</p>
            )}
            <div className="mt-4 grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              {compositions.map((c) => (
                <button
                  key={c.code}
                  type="button"
                  onClick={() => setSelectedCode(c.code)}
                  className={`rounded-2xl border p-4 text-left shadow-sm transition hover:border-primary ${
                    c.code === selectedCode ? "border-primary bg-primary/5" : "border-border bg-background"
                  }`}
                >
                  <div className="flex items-start justify-between gap-2">
                    <span className="font-semibold text-foreground">{c.title}</span>
                    <ArrowRight className="h-4 w-4 shrink-0 text-primary" />
                  </div>
                  {c.description && <p className="mt-1 text-sm text-muted-foreground">{c.description}</p>}
                </button>
              ))}
            </div>
          </section>

          {selected && (
            <section className="space-y-4">
              <div className="rounded-2xl border border-border bg-card p-5 shadow-sm">
                <h2 className="text-lg font-semibold text-foreground">2. What you will need — {selected.title}</h2>
                <p className="mt-1 text-sm text-muted-foreground">
                  These are the requirements HPA inspectors assess for this facility type. Where a requirement maps
                  to a marketplace category, a supplier link is shown; items marked{" "}
                  <span className="text-amber-700">licensed suppliers only</span> can only be bought from sellers
                  holding the required credential.
                </p>
                {modulesQuery.isLoading && (
                  <p className="mt-3 flex items-center gap-2 text-sm text-muted-foreground">
                    <Loader2 className="h-4 w-4 animate-spin" /> Loading requirements…
                  </p>
                )}
                {modulesQuery.isError && (
                  <p className="mt-3 text-sm text-red-600">Could not load the requirement modules right now.</p>
                )}
                {resolveQuery.isError && (
                  <p className="mt-3 text-xs text-amber-700">
                    Supplier links are unavailable right now — the requirement list below is still complete.
                  </p>
                )}
              </div>
              {modules.map((m) => (
                <ModuleRequirements key={m.code} module={m} resolutions={resolutions} />
              ))}
            </section>
          )}

          <p className="rounded-lg border border-border bg-background px-4 py-2 text-xs text-muted-foreground">
            This guide is informational: requirements come from the published HPA inspection catalogue, and supplier
            links point to marketplace sellers in the matching category — not endorsements. Registration and
            licensing still follow the formal application process.
          </p>
        </div>
      </PageShell>
    </AppLayout>
  );
}
