"use client";

/**
 * Form schema builder — extension form definitions with JSON preview.
 * Route: /clinical-tools/forms
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import { ArrowLeft, FileJson2, Loader2, Plus } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useCreateExtensionForm,
  useExtensionForms,
} from "@/hooks/queries/useClinicalExtensions";

function asRecord(v: unknown): Record<string, unknown> {
  return v && typeof v === "object" ? (v as Record<string, unknown>) : {};
}

function readStr(r: Record<string, unknown>, ...keys: string[]) {
  for (const k of keys) {
    const x = r[k];
    if (typeof x === "string" && x.length > 0) return x;
  }
  return "";
}

const STATUS_BADGE: Record<string, string> = {
  ACTIVE: "bg-emerald-100 text-primary-hover",
  DRAFT: "bg-neutral-100 text-foreground",
  PUBLISHED: "bg-cyan-100 text-cyan-800",
  DEPRECATED: "bg-neutral-100 text-muted-foreground",
};

export default function ClinicalFormBuilderPage() {
  const formsQ = useExtensionForms(0, 100);
  const createM = useCreateExtensionForm();

  const [selectedIdx, setSelectedIdx] = useState(0);
  const [showCreate, setShowCreate] = useState(false);
  const [draftName, setDraftName] = useState("");
  const [draftVersion, setDraftVersion] = useState("1.0.0");
  const [draftStatus, setDraftStatus] = useState("DRAFT");
  const [schemaParseError, setSchemaParseError] = useState<string | null>(null);
  const [draftSchema, setDraftSchema] = useState(
    JSON.stringify(
      {
        type: "object",
        properties: {
          chiefComplaint: { type: "string" },
          painScore: { type: "integer", minimum: 0, maximum: 10 },
        },
        required: ["chiefComplaint"],
      },
      null,
      2,
    ),
  );

  const forms = useMemo(() => (formsQ.data ?? []).map(asRecord), [formsQ.data]);

  const previewJson = useMemo(() => {
    if (forms.length === 0) return "";
    const f = forms[Math.min(selectedIdx, forms.length - 1)];
    const schema = f.jsonSchema ?? f.schema ?? f.definition ?? f;
    try {
      return JSON.stringify(schema, null, 2);
    } catch {
      return String(schema ?? "");
    }
  }, [forms, selectedIdx]);

  const submitCreate = () => {
    if (!draftName.trim()) return;
    let schemaObj: unknown;
    try {
      schemaObj = JSON.parse(draftSchema);
      setSchemaParseError(null);
    } catch {
      setSchemaParseError("JSON Schema must be valid JSON.");
      return;
    }
    createM.mutate(
      {
        name: draftName.trim(),
        version: draftVersion.trim(),
        status: draftStatus,
        jsonSchema: schemaObj,
      },
      {
        onSuccess: () => {
          setShowCreate(false);
          setDraftName("");
          setSelectedIdx(0);
        },
      },
    );
  };

  return (
    <AppLayout>
      <PageShell
        title="Form schema builder"
        subtitle="Extension form definitions — versioned JSON Schema for clinical capture flows"
        icon={<FileJson2 className="h-6 w-6" />}
      >
        <div className="mb-4 flex flex-wrap items-center gap-2">
          <Link
            href="/clinical-tools"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" />
            Clinical tools
          </Link>
          <span className="text-muted-foreground">·</span>
          <Link href="/clinical-tools/rules" className="text-sm text-cyan-700 hover:underline">
            Rules engine
          </Link>
        </div>

        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm text-muted-foreground">Lists forms from GET /internal/v1/extensions/forms.</p>
          <button
            type="button"
            onClick={() => setShowCreate((s) => !s)}
            className="inline-flex items-center gap-2 rounded-lg bg-cyan-600 px-3 py-2 text-sm font-medium text-white hover:bg-cyan-700"
          >
            <Plus className="h-4 w-4" />
            Create Form
          </button>
        </div>

        {showCreate && (
          <div className="mb-6 rounded-xl border-2 border-cyan-200 bg-cyan-50/40 p-4 space-y-3 text-sm">
            <div className="grid gap-3 sm:grid-cols-3">
              <label className="block">
                <span className="text-xs font-medium text-muted-foreground">Name</span>
                <input
                  className="mt-1 w-full rounded-lg border border-border px-3 py-2"
                  value={draftName}
                  onChange={(e) => setDraftName(e.target.value)}
                  placeholder="Triage intake v1"
                />
              </label>
              <label className="block">
                <span className="text-xs font-medium text-muted-foreground">Version</span>
                <input
                  className="mt-1 w-full rounded-lg border border-border px-3 py-2 font-mono text-xs"
                  value={draftVersion}
                  onChange={(e) => setDraftVersion(e.target.value)}
                />
              </label>
              <label className="block">
                <span className="text-xs font-medium text-muted-foreground">Status</span>
                <select
                  className="mt-1 w-full rounded-lg border border-border px-3 py-2"
                  value={draftStatus}
                  onChange={(e) => setDraftStatus(e.target.value)}
                >
                  <option value="DRAFT">DRAFT</option>
                  <option value="ACTIVE">ACTIVE</option>
                  <option value="PUBLISHED">PUBLISHED</option>
                </select>
              </label>
            </div>
            <label className="block">
              <span className="text-xs font-medium text-muted-foreground">JSON Schema</span>
              <textarea
                className="mt-1 w-full rounded-lg border border-border px-3 py-2 font-mono text-xs"
                rows={12}
                value={draftSchema}
                onChange={(e) => setDraftSchema(e.target.value)}
              />
            </label>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setShowCreate(false)}
                className="rounded-lg px-3 py-1.5 text-sm text-muted-foreground hover:bg-neutral-100"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={createM.isPending || !draftName.trim()}
                onClick={submitCreate}
                className="rounded-lg bg-cyan-600 px-4 py-1.5 text-sm font-medium text-white disabled:opacity-50"
              >
                {createM.isPending ? "Saving…" : "Save form"}
              </button>
            </div>
            {schemaParseError && <p className="text-xs text-danger">{schemaParseError}</p>}
            {createM.isError && (
              <p className="text-xs text-danger">
                Create failed — wire POST /internal/v1/extensions/forms on the BFF when ready.
              </p>
            )}
          </div>
        )}

        <div className="grid gap-6 lg:grid-cols-2">
          <div className="overflow-hidden rounded-xl border border-border bg-card shadow-sm">
            {formsQ.isLoading && (
              <div className="flex items-center justify-center gap-2 py-16 text-muted-foreground">
                <Loader2 className="h-5 w-5 animate-spin" /> Loading form schemas…
              </div>
            )}
            {formsQ.isError && (
              <p className="p-6 text-sm text-danger">Could not load forms from the BFF.</p>
            )}
            {!formsQ.isLoading && !formsQ.isError && (
              <table className="min-w-full text-sm">
                <thead className="border-b border-border bg-background text-left text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                  <tr>
                    <th className="px-4 py-3">Name</th>
                    <th className="px-4 py-3">Version</th>
                    <th className="px-4 py-3">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {forms.length === 0 && (
                    <tr>
                      <td colSpan={3} className="px-4 py-10 text-center text-muted-foreground">
                        No form schemas returned. Create one locally once the API exists.
                      </td>
                    </tr>
                  )}
                  {forms.map((f, i) => {
                    const st = readStr(f, "status", "state").toUpperCase() || "DRAFT";
                    return (
                      <tr
                        key={readStr(f, "id", "formId") || String(i)}
                        onClick={() => setSelectedIdx(i)}
                        className={`cursor-pointer hover:bg-cyan-50/50 ${selectedIdx === i ? "bg-cyan-50/80" : ""}`}
                      >
                        <td className="px-4 py-3 font-medium">{readStr(f, "name", "title", "formName")}</td>
                        <td className="px-4 py-3 font-mono text-xs">{readStr(f, "version", "semver")}</td>
                        <td className="px-4 py-3">
                          <span
                            className={`inline-flex rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_BADGE[st] ?? "bg-neutral-100 text-foreground"}`}
                          >
                            {st}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </div>

          <div className="rounded-xl border border-border bg-neutral-900 p-4 text-cyan-50 shadow-sm">
            <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold text-white">
              <FileJson2 className="h-4 w-4 text-cyan-400" />
              JSON schema preview
            </h3>
            <p className="mb-2 text-xs text-muted-foreground">Selected row or empty when no forms.</p>
            <pre className="max-h-[min(70vh,520px)] overflow-auto rounded-lg bg-black/40 p-3 text-xs leading-relaxed text-cyan-100">
              {previewJson || "// No schema in list response"}
            </pre>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
