import { FundoStudioWorkspace } from "@/components/learning/FundoStudioWorkspace";

export default function FundoStudioMediaVoiceoversPage() {
  return (
    <FundoStudioWorkspace title="Media Voiceovers" subtitle="Voiceover script drafts and future TTS adapter handoff queue.">
      <div className="rounded border border-border bg-card p-4 text-sm text-foreground">
        Voiceover generation is provider-agnostic and currently draft-first. No external provider is hard-coded by default.
      </div>
    </FundoStudioWorkspace>
  );
}
