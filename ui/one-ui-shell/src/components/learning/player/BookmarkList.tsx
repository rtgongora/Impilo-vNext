"use client";

import { useState } from "react";
import { BookmarkPlus, Trash2 } from "lucide-react";
import type { FundoMediaBookmark } from "@/hooks/queries/useFundoPlayer";
import { formatClock } from "./PlaybackControls";

export interface BookmarkListProps {
  bookmarks: FundoMediaBookmark[];
  currentTime: number;
  onJump: (seconds: number) => void;
  onAdd: (positionSeconds: number, note: string) => void;
  onDelete: (bookmarkId: string) => void;
  busy?: boolean;
}

export function BookmarkList({ bookmarks, currentTime, onJump, onAdd, onDelete, busy }: BookmarkListProps) {
  const [note, setNote] = useState("");

  return (
    <div className="rounded-lg border border-border bg-card p-3" data-testid="player-bookmarks">
      <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">Bookmarks</p>
      <div className="mb-2 flex items-center gap-2">
        <input
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="Note (optional)"
          aria-label="Bookmark note"
          className="min-w-0 flex-1 rounded border border-border bg-background px-2 py-1 text-sm text-foreground"
          data-testid="player-bookmark-note"
        />
        <button
          type="button"
          disabled={busy}
          onClick={() => {
            onAdd(Math.floor(currentTime), note.trim());
            setNote("");
          }}
          className="inline-flex shrink-0 items-center gap-1 rounded bg-teal-700 px-2 py-1 text-xs font-medium text-white hover:bg-teal-800 disabled:opacity-50"
          data-testid="player-bookmark-add"
        >
          <BookmarkPlus className="h-3.5 w-3.5" /> {formatClock(currentTime)}
        </button>
      </div>
      {bookmarks.length === 0 ? (
        <p className="text-xs text-muted-foreground">No bookmarks yet — mark moments to revisit.</p>
      ) : (
        <ul className="space-y-1">
          {bookmarks.map((bookmark) => (
            <li key={bookmark.id} className="flex items-center gap-2" data-testid="player-bookmark-item">
              <button
                type="button"
                onClick={() => onJump(bookmark.positionSeconds)}
                className="flex min-w-0 flex-1 items-center gap-2 rounded px-2 py-1 text-left text-sm text-foreground hover:bg-muted"
              >
                <span className="shrink-0 text-xs tabular-nums text-teal-700">
                  {formatClock(bookmark.positionSeconds)}
                </span>
                <span className="truncate text-xs text-muted-foreground">{bookmark.note ?? "Bookmark"}</span>
              </button>
              <button
                type="button"
                onClick={() => onDelete(bookmark.id)}
                className="shrink-0 rounded p-1 text-muted-foreground hover:bg-muted hover:text-destructive"
                aria-label="Delete bookmark"
                data-testid="player-bookmark-delete"
              >
                <Trash2 className="h-3.5 w-3.5" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
