"use client";

import { Hand } from "lucide-react";
import { useRaiseHand, useSendReaction } from "@/hooks/queries/useMeeting";

const REACTIONS = ["👏", "👍", "❤️", "😂", "🎉"] as const;

/**
 * Raise-hand + emoji reactions. Both are governed realtime events on the meeting conversation
 * (khuluma dispatches meeting.hand.raised/lowered and meeting.reaction; each is audited via the
 * outbox) — no schema, no LiveKit data-channel side channel.
 */
export function ReactionsBar({
  conversationId,
  handRaised,
  onHandToggled,
}: {
  conversationId: string;
  handRaised: boolean;
  onHandToggled: (raised: boolean) => void;
}) {
  const raiseHand = useRaiseHand(conversationId);
  const react = useSendReaction(conversationId);

  return (
    <div className="flex items-center gap-1" data-testid="reactions-bar">
      <button
        type="button"
        aria-label={handRaised ? "Lower hand" : "Raise hand"}
        aria-pressed={handRaised}
        data-testid="raise-hand"
        className={`flex items-center gap-1 rounded-md px-2 py-1.5 text-xs ${
          handRaised ? "bg-amber-500 text-black" : "bg-neutral-800 text-white hover:bg-neutral-700"
        }`}
        onClick={() => {
          const next = !handRaised;
          raiseHand.mutate(next);
          onHandToggled(next);
        }}
      >
        <Hand className="h-3.5 w-3.5" />
        {handRaised ? "Lower" : "Raise"}
      </button>
      {REACTIONS.map((emoji) => (
        <button
          key={emoji}
          type="button"
          aria-label={`React ${emoji}`}
          data-testid={`reaction-${emoji}`}
          className="rounded-md bg-neutral-800 px-2 py-1.5 text-sm hover:bg-neutral-700"
          onClick={() => react.mutate(emoji)}
        >
          {emoji}
        </button>
      ))}
    </div>
  );
}
