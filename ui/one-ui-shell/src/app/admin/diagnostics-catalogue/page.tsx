"use client";

/**
 * Admin — Diagnostics Service Catalogue editor (O13, spec §9 / §17 admin).
 * Route: /admin/diagnostics-catalogue | Zone: admin | Guard: role ADMIN
 *
 * Curate the service catalogue & fulfilment directory, the orderable test/procedure catalogue, and
 * specimen configuration. Real read/write via the experience-bff diagnostics proxy → OROS
 * admin-config. JSON-document editor (the catalogue shape evolves independently).
 */

import { useEffect, useState } from "react";
import { Loader2, Save } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useCatalogue, useSaveCatalogue } from "@/hooks/queries/useDiagnosticsOrders";

const CATALOGUES = [
  { segment: "services", adminPath: "service-catalogue", label: "Service catalogue & fulfilment directory" },
  { segment: "orderables", adminPath: "orderable-catalogue", label: "Orderable test/procedure catalogue" },
  { segment: "specimen-config", adminPath: "specimen-config", label: "Specimen configuration" },
] as const;

export default function DiagnosticsCataloguePage() {
  const [idx, setIdx] = useState(0);
  const active = CATALOGUES[idx];
  const catalogueQ = useCatalogue(active.segment);
  const saveMut = useSaveCatalogue();
  const [draft, setDraft] = useState("{}");
  const [parseError, setParseError] = useState<string | null>(null);

  useEffect(() => {
    if (catalogueQ.data?.data !== undefined) {
      setDraft(JSON.stringify(catalogueQ.data.data ?? {}, null, 2));
    }
  }, [catalogueQ.data, idx]);

  function save() {
    setParseError(null);
    let parsed: Record<string, unknown>;
    try {
      parsed = JSON.parse(draft);
    } catch {
      setParseError("Invalid JSON — fix and retry.");
      return;
    }
    saveMut.mutate({ adminPath: active.adminPath, body: parsed });
  }

  return (
    <AppLayout>
      <PageShell title="Diagnostics Catalogue" subtitle="Curate the service catalogue, orderables & specimen config">
        <div className="mb-4 flex flex-wrap gap-2">
          {CATALOGUES.map((c, i) => (
            <button
              key={c.segment}
              type="button"
              onClick={() => setIdx(i)}
              className={`rounded-full px-3 py-1 text-xs font-medium ${
                i === idx ? "bg-primary text-primary-foreground" : "bg-muted hover:bg-muted/70"
              }`}
            >
              {c.label}
            </button>
          ))}
        </div>

        {catalogueQ.isLoading ? (
          <div className="flex items-center justify-center gap-2 p-12 text-sm text-muted-foreground">
            <Loader2 className="h-5 w-5 animate-spin" /> Loading catalogue…
          </div>
        ) : catalogueQ.isError ? (
          <div className="p-8 text-center text-sm text-danger">Unable to load the catalogue from OROS.</div>
        ) : (
          <div className="space-y-3">
            <textarea
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              aria-label="Catalogue JSON"
              className="impilo-pill-input min-h-[360px] w-full rounded-2xl py-3 font-mono text-xs"
              spellCheck={false}
            />
            <div className="flex items-center gap-3">
              <button
                type="button"
                onClick={save}
                disabled={saveMut.isPending}
                className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50"
              >
                {saveMut.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                Save catalogue
              </button>
              {parseError && <span className="text-sm text-danger" role="alert">{parseError}</span>}
              {saveMut.isError && <span className="text-sm text-danger" role="alert">Save failed. Retry.</span>}
              {saveMut.isSuccess && !saveMut.isPending && (
                <span className="text-sm text-success" role="status">Saved.</span>
              )}
            </div>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
