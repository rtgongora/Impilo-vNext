"use client";

import { useState } from "react";
import Link from "next/link";
import { ArrowLeft, Loader2, Plug } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useCreateIntegrationMappingTemplate,
  useIntegrationMappingTemplates,
} from "@/hooks/queries/useIntegrationHub";

export default function IntegrationTemplatesAdminPage() {
  const { data: templates, isLoading } = useIntegrationMappingTemplates();
  const createTemplate = useCreateIntegrationMappingTemplate();
  const [form, setForm] = useState({
    name: "",
    sourceSystem: "",
    targetSystem: "",
    mappingSpec: "{}",
  });

  return (
    <AppLayout>
      <PageShell title="Integration mapping templates" subtitle="Adapter mapping registry via integration-hub BFF">
        <Link href="/admin" className="mb-4 inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
          <ArrowLeft className="h-4 w-4" />
          Administration
        </Link>

        <form
          className="mb-6 grid gap-3 rounded-xl border border-border bg-card p-4 sm:grid-cols-2"
          onSubmit={(e) => {
            e.preventDefault();
            let mappingSpec: unknown = form.mappingSpec;
            try {
              mappingSpec = JSON.parse(form.mappingSpec);
            } catch {
              /* send raw string */
            }
            createTemplate.mutate({
              name: form.name,
              sourceSystem: form.sourceSystem,
              targetSystem: form.targetSystem,
              mappingSpec,
            });
          }}
        >
          <h3 className="sm:col-span-2 flex items-center gap-2 text-sm font-semibold text-foreground">
            <Plug className="h-4 w-4 text-violet-600" />
            Register mapping template
          </h3>
          <input
            className="rounded-lg border border-border px-3 py-2 text-sm"
            placeholder="Template name"
            value={form.name}
            onChange={(e) => setForm((p) => ({ ...p, name: e.target.value }))}
            required
          />
          <input
            className="rounded-lg border border-border px-3 py-2 text-sm"
            placeholder="Source system"
            value={form.sourceSystem}
            onChange={(e) => setForm((p) => ({ ...p, sourceSystem: e.target.value }))}
            required
          />
          <input
            className="rounded-lg border border-border px-3 py-2 text-sm"
            placeholder="Target system"
            value={form.targetSystem}
            onChange={(e) => setForm((p) => ({ ...p, targetSystem: e.target.value }))}
            required
          />
          <textarea
            className="sm:col-span-2 rounded-lg border border-border px-3 py-2 font-mono text-xs"
            rows={4}
            value={form.mappingSpec}
            onChange={(e) => setForm((p) => ({ ...p, mappingSpec: e.target.value }))}
          />
          <button
            type="submit"
            disabled={createTemplate.isPending}
            className="sm:col-span-2 rounded-lg bg-violet-700 px-4 py-2 text-sm font-medium text-white hover:bg-violet-800 disabled:opacity-50"
          >
            {createTemplate.isPending ? "Saving…" : "Save template"}
          </button>
        </form>

        {isLoading ? (
          <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
        ) : (
          <ul className="divide-y divide-gray-100 rounded-xl border border-border bg-card">
            {(templates ?? []).length === 0 ? (
              <li className="p-4 text-sm text-muted-foreground">No mapping templates registered yet.</li>
            ) : (
              templates?.map((row, index) => (
                <li key={String(row.id ?? index)} className="p-4">
                  <p className="text-sm font-medium text-foreground">{String(row.name ?? row.id ?? "Template")}</p>
                  <p className="text-xs text-muted-foreground">
                    {String(row.sourceSystem ?? "—")} → {String(row.targetSystem ?? "—")}
                  </p>
                </li>
              ))
            )}
          </ul>
        )}
      </PageShell>
    </AppLayout>
  );
}
