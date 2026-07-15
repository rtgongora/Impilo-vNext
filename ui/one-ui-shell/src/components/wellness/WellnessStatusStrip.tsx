"use client";

import { useState } from "react";
import { Loader2, Plus, Sparkles, X } from "lucide-react";
import {
  useActiveStatuses,
  useCreateStatus,
  useDeleteStatus,
  type SocialStatus,
} from "@/hooks/queries/useSimbaSocial";
import { useAuthStore } from "@/hooks/useAuthStore";

const MOODS: [string, string][] = [
  ["HAPPY", "😊"],
  ["MOTIVATED", "💪"],
  ["CALM", "😌"],
  ["TIRED", "😴"],
  ["STRESSED", "😰"],
  ["GRATEFUL", "🙏"],
];

const TTL_OPTIONS: [number, string][] = [
  [6, "6 hours"],
  [12, "12 hours"],
  [24, "1 day"],
  [72, "3 days"],
];

function moodEmoji(mood?: string | null): string {
  if (!mood) return "💬";
  return MOODS.find(([m]) => m === mood)?.[1] ?? "💬";
}

/** Ephemeral wellness-status strip + composer — short, expiring "how I'm doing" notes. */
export function WellnessStatusStrip() {
  const user = useAuthStore((s) => s.user);
  const myCpid = user?.id;

  const statusesQ = useActiveStatuses();
  const createStatus = useCreateStatus();
  const deleteStatus = useDeleteStatus();

  const [open, setOpen] = useState(false);
  const [text, setText] = useState("");
  const [mood, setMood] = useState("HAPPY");
  const [visibility, setVisibility] = useState("PUBLIC_WITHIN_IMPILO");
  const [ttl, setTtl] = useState(24);
  const [error, setError] = useState<string | null>(null);

  const statuses = statusesQ.data?.data ?? [];

  function submit() {
    if (!text.trim()) return;
    setError(null);
    createStatus.mutate(
      { text, mood, visibility, ttl_hours: ttl },
      {
        onSuccess: () => {
          setText("");
          setOpen(false);
        },
        onError: (e) => setError(e.message),
      },
    );
  }

  return (
    <section className="rounded-xl border border-slate-200 bg-white p-4">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="inline-flex items-center gap-2 text-sm font-semibold text-slate-700">
          <Sparkles className="h-4 w-4 text-emerald-600" />
          Right now
        </h2>
        <button
          type="button"
          onClick={() => setOpen((o) => !o)}
          className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-700"
        >
          <Plus className="h-3.5 w-3.5" />
          Share a status
        </button>
      </div>

      {open && (
        <div className="mb-4 space-y-3 rounded-lg border border-slate-200 bg-slate-50 p-3">
          <input
            value={text}
            onChange={(e) => setText(e.target.value)}
            maxLength={280}
            placeholder="How are you doing right now?"
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
          />
          <div className="flex flex-wrap gap-1">
            {MOODS.map(([m, emoji]) => (
              <button
                key={m}
                type="button"
                onClick={() => setMood(m)}
                aria-pressed={mood === m}
                className={`rounded-full px-2.5 py-1 text-sm ${
                  mood === m ? "bg-emerald-600 text-white" : "bg-white text-slate-600"
                }`}
              >
                {emoji}
              </button>
            ))}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <select
              value={visibility}
              onChange={(e) => setVisibility(e.target.value)}
              className="rounded-lg border border-slate-300 px-2 py-1.5 text-xs"
            >
              <option value="PRIVATE">Only me</option>
              <option value="FOLLOWER">My followers</option>
              <option value="PUBLIC_WITHIN_IMPILO">Everyone on Impilo</option>
            </select>
            <select
              value={ttl}
              onChange={(e) => setTtl(Number(e.target.value))}
              className="rounded-lg border border-slate-300 px-2 py-1.5 text-xs"
            >
              {TTL_OPTIONS.map(([h, label]) => (
                <option key={h} value={h}>
                  Expires in {label}
                </option>
              ))}
            </select>
            <button
              type="button"
              onClick={submit}
              disabled={createStatus.isPending || !text.trim()}
              className="ml-auto inline-flex items-center gap-2 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
            >
              {createStatus.isPending && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
              Post status
            </button>
          </div>
          {error && <p className="text-xs text-red-600">{error}</p>}
        </div>
      )}

      {statusesQ.isLoading ? (
        <div className="flex items-center justify-center py-4 text-slate-400">
          <Loader2 className="h-5 w-5 animate-spin" />
        </div>
      ) : statuses.length === 0 ? (
        <p className="py-2 text-center text-xs text-slate-400">
          No active statuses. Be the first to share how you&apos;re doing.
        </p>
      ) : (
        <div className="flex gap-2 overflow-x-auto pb-1">
          {statuses.map((s: SocialStatus) => (
            <div
              key={s.statusId}
              className="relative min-w-[9rem] max-w-[12rem] shrink-0 rounded-lg border border-slate-200 bg-white p-3"
            >
              {s.actorCpid === myCpid && (
                <button
                  type="button"
                  aria-label="Remove status"
                  onClick={() => deleteStatus.mutate({ statusId: s.statusId })}
                  className="absolute right-1 top-1 text-slate-300 hover:text-red-500"
                >
                  <X className="h-3.5 w-3.5" />
                </button>
              )}
              <div className="mb-1 text-lg">{moodEmoji(s.mood)}</div>
              <p className="line-clamp-3 text-xs text-slate-700">{s.text}</p>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
