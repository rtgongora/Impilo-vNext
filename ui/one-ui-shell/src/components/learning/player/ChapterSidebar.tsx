"use client";

import { ListVideo } from "lucide-react";
import type { FundoMediaChapter } from "@/hooks/queries/useFundoPlayer";
import { formatClock } from "./PlaybackControls";

export interface ChapterSidebarProps {
  chapters: FundoMediaChapter[];
  currentTime: number;
  onJump: (seconds: number) => void;
}

export function ChapterSidebar({ chapters, currentTime, onJump }: ChapterSidebarProps) {
  if (chapters.length === 0) return null;
  // The active chapter is the last one whose start precedes the playhead.
  const activeId = [...chapters]
    .sort((a, b) => a.startSeconds - b.startSeconds)
    .filter((c) => c.startSeconds <= currentTime)
    .at(-1)?.id;

  return (
    <div className="rounded-lg border border-border bg-card p-3" data-testid="player-chapters">
      <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        <ListVideo className="h-3.5 w-3.5" /> Chapters
      </p>
      <ul className="space-y-1">
        {chapters.map((chapter) => (
          <li key={chapter.id}>
            <button
              type="button"
              onClick={() => onJump(chapter.startSeconds)}
              className={`flex w-full items-center justify-between gap-2 rounded px-2 py-1.5 text-left text-sm hover:bg-muted ${
                chapter.id === activeId ? "bg-teal-50 font-medium text-teal-900" : "text-foreground"
              }`}
              data-testid="player-chapter-item"
            >
              <span className="truncate">{chapter.title}</span>
              <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
                {formatClock(chapter.startSeconds)}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
