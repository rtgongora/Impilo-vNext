"use client";

import { FileText, Loader2, Send } from "lucide-react";
import {
  useEvaluatePublicHealthBriefExport,
  useExportPublicHealthBrief,
  useGeneratePublicHealthBrief,
  usePublicHealthBriefs,
  usePublishPublicHealthBrief,
} from "@/hooks/queries/usePublicHealth";

export function PublicHealthReportsPanel() {
  const briefsQ = usePublicHealthBriefs();
  const generateM = useGeneratePublicHealthBrief();
  const publishM = usePublishPublicHealthBrief();
  const evaluateExportM = useEvaluatePublicHealthBriefExport();
  const exportM = useExportPublicHealthBrief();
  const briefs = briefsQ.data ?? [];

  function downloadBase64(fileName: string, contentType: string, contentBase64: string) {
    const bytes = Uint8Array.from(atob(contentBase64), (c) => c.charCodeAt(0));
    const blob = new Blob([bytes], { type: contentType });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = fileName;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  }

  function handleExport(briefId: string, format: "PDF" | "CSV") {
    exportM.mutate(
      { briefId, format },
      {
        onSuccess: (result) => {
          if (!result.contentBase64) return;
          downloadBase64(result.fileName, result.contentType, result.contentBase64);
        },
      },
    );
  }

  return (
    <div className="bg-card rounded-lg border border-border p-5 space-y-4">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-foreground flex items-center gap-2">
          <FileText className="h-4 w-4 text-sky-600" /> Intelligence briefs
        </h3>
        <button
          type="button"
          disabled={generateM.isPending}
          onClick={() => generateM.mutate({ title: "Public Health Situation Brief", briefType: "SITUATION" })}
          className="text-xs px-3 py-1.5 rounded-lg bg-sky-600 text-white hover:bg-sky-700 disabled:opacity-50"
        >
          {generateM.isPending ? "Generating…" : "Generate draft"}
        </button>
      </div>
      <p className="text-[10px] text-muted-foreground">
        Drafts are built deterministically from the operations-home aggregate until Nompilo LLM integration is enabled.
      </p>
      {briefsQ.isPending ? (
        <div className="flex items-center gap-2 text-sm text-muted-foreground py-4">
          <Loader2 className="h-4 w-4 animate-spin" /> Loading briefs…
        </div>
      ) : briefs.length === 0 ? (
        <p className="text-xs text-muted-foreground">No briefs yet. Generate a draft to review and publish.</p>
      ) : (
        <div className="space-y-3">
          {briefs.slice(0, 5).map((b) => (
            <div key={b.id} className="border border-border rounded-lg p-3">
              <div className="flex items-center justify-between gap-2 mb-2">
                <p className="text-sm font-medium text-foreground">{b.title}</p>
                <span className="text-[10px] px-2 py-0.5 rounded-full bg-neutral-100 text-foreground">{b.status}</span>
              </div>
              {b.summaryMarkdown && (
                <pre className="text-[10px] text-muted-foreground whitespace-pre-wrap font-sans max-h-24 overflow-y-auto">
                  {b.summaryMarkdown}
                </pre>
              )}
              <div className="mt-2 flex items-center gap-3">
                {b.status !== "PUBLISHED" && (
                  <button
                    type="button"
                    disabled={publishM.isPending}
                    onClick={() => publishM.mutate(b.id)}
                    className="inline-flex items-center gap-1 text-[10px] text-sky-700 hover:underline"
                  >
                    <Send className="h-3 w-3" /> Publish
                  </button>
                )}
                <button
                  type="button"
                  disabled={evaluateExportM.isPending}
                  onClick={() => evaluateExportM.mutate({ briefId: b.id, format: "PDF" })}
                  className="text-[10px] text-primary-hover hover:underline disabled:opacity-50"
                >
                  Check PDF export governance
                </button>
                <button
                  type="button"
                  disabled={evaluateExportM.isPending}
                  onClick={() => evaluateExportM.mutate({ briefId: b.id, format: "CSV" })}
                  className="text-[10px] text-primary-hover hover:underline disabled:opacity-50"
                >
                  Check CSV export governance
                </button>
                <button
                  type="button"
                  disabled={exportM.isPending}
                  onClick={() => handleExport(b.id, "PDF")}
                  className="text-[10px] text-sky-700 hover:underline disabled:opacity-50"
                >
                  Export PDF
                </button>
                <button
                  type="button"
                  disabled={exportM.isPending}
                  onClick={() => handleExport(b.id, "CSV")}
                  className="text-[10px] text-sky-700 hover:underline disabled:opacity-50"
                >
                  Export CSV
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
