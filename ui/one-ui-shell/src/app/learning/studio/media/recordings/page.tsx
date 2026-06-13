import { FundoStudioWorkspace } from "@/components/learning/FundoStudioWorkspace";

export default function FundoStudioMediaRecordingsPage() {
  return (
    <FundoStudioWorkspace title="Media Recordings" subtitle="Screen recording metadata queue and draft review list.">
      <div className="rounded border border-border bg-card p-4 text-sm text-foreground">
        Recording drafts are created in Studio Media and can be linked to lessons as draft/reviewed/published assets.
      </div>
    </FundoStudioWorkspace>
  );
}
