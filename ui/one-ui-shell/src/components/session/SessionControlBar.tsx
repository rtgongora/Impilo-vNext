"use client";

import { ControlBar } from "@livekit/components-react";

export interface SessionControlsConfig {
  microphone?: boolean;
  camera?: boolean;
  screenShare?: boolean;
  leave?: boolean;
  settings?: boolean;
}

/**
 * Session control bar — thin governance wrapper around the LiveKit ControlBar.
 * `audience` (subscribe-only) participants never get publish controls no
 * matter what the caller passes; leave remains available so viewers can exit.
 */
export function SessionControlBar({
  controls,
  audience = false,
}: {
  controls?: SessionControlsConfig;
  audience?: boolean;
}) {
  const resolved = {
    microphone: audience ? false : controls?.microphone ?? true,
    camera: audience ? false : controls?.camera ?? true,
    screenShare: audience ? false : controls?.screenShare ?? false,
    chat: false,
    leave: controls?.leave ?? true,
    settings: audience ? false : controls?.settings ?? false,
  };

  return (
    <div className="border-t border-white/10 bg-black/70" data-testid="session-control-bar">
      <ControlBar variation="minimal" controls={resolved} />
    </div>
  );
}
