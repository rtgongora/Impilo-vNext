"use client";

import { PreJoin, type LocalUserChoices } from "@livekit/components-react";

/**
 * Pre-join panel — device check and camera preview before entering a
 * governed session, themed to the app shell. Thin wrapper over the LiveKit
 * PreJoin component so device handling stays upstream.
 */
export function PreJoinPanel({
  defaults,
  joinLabel = "Join session",
  onSubmit,
  onError,
}: {
  defaults?: Partial<LocalUserChoices>;
  joinLabel?: string;
  onSubmit?: (values: LocalUserChoices) => void;
  onError?: (error: Error) => void;
}) {
  return (
    <div
      data-testid="session-prejoin"
      className="overflow-hidden rounded-2xl border border-border bg-neutral-950 text-white"
    >
      <PreJoin
        defaults={defaults}
        joinLabel={joinLabel}
        onSubmit={onSubmit}
        onError={onError}
        persistUserChoices={false}
      />
    </div>
  );
}
