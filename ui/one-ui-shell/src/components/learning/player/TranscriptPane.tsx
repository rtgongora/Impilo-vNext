"use client";

import { FileText } from "lucide-react";

export interface TranscriptPaneProps {
  transcript?: string | null;
}

/**
 * Renders the asset transcript when one exists on the media asset.
 *
 * HONEST SEAM (LEARNING_RECORDING W4): there is no speech-recognition service
 * in the estate, so transcripts are never auto-generated. They arrive through
 * offline production — an author (or an external transcription workflow)
 * writes the transcript onto the lrn_media_asset (studio surface / document
 * register-external companion flows). When absent, this pane renders nothing:
 * no fake "generating transcript…" theatre.
 */
export function TranscriptPane({ transcript }: TranscriptPaneProps) {
  if (!transcript || !transcript.trim()) return null;
  return (
    <div className="rounded-lg border border-border bg-card p-3" data-testid="player-transcript">
      <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        <FileText className="h-3.5 w-3.5" /> Transcript
      </p>
      <div className="max-h-64 overflow-y-auto whitespace-pre-wrap text-sm text-foreground">{transcript}</div>
    </div>
  );
}
