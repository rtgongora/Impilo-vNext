import { FundoStudioWorkspace } from "@/components/learning/FundoStudioWorkspace";

export default function FundoStudioMediaAssetPage({ params }: { params: { mediaId: string } }) {
  return (
    <FundoStudioWorkspace title="Media Asset Detail" subtitle="Media metadata, transcript/caption references, and lesson linking.">
      <div className="rounded border border-border bg-card p-4 text-sm text-foreground">
        Media asset reference: <span className="font-mono">{params.mediaId}</span>
      </div>
    </FundoStudioWorkspace>
  );
}
