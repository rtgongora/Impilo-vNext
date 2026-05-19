"use client";

/**
 * ThreeColumnTimelineLayout — Desktop/tablet three-column shell for the
 * social timeline experience.
 *
 *  ┌──────────────┬──────────────────────────────┬───────────────────┐
 *  │  left rail   │  main timeline (feed)        │  right rail       │
 *  │  - nav       │  - composer                  │  - widgets        │
 *  │  - shortcuts │  - feed list                 │  - suggestions    │
 *  └──────────────┴──────────────────────────────┴───────────────────┘
 *
 *  Mobile (<lg): single column, feed-first, side rails collapse below.
 */

import type { ReactNode } from "react";

interface Props {
  left: ReactNode;
  center: ReactNode;
  right: ReactNode;
}

export function ThreeColumnTimelineLayout({ left, center, right }: Props) {
  return (
    <div className="mx-auto grid w-full max-w-[1400px] grid-cols-1 gap-5 lg:grid-cols-[280px_minmax(0,1fr)_320px] xl:grid-cols-[300px_minmax(0,1fr)_340px]">
      <aside className="order-2 space-y-4 lg:order-1 lg:sticky lg:top-4 lg:max-h-[calc(100vh-2rem)] lg:overflow-y-auto">
        {left}
      </aside>
      <main className="order-1 min-w-0 space-y-4 lg:order-2">{center}</main>
      <aside className="order-3 space-y-4 lg:sticky lg:top-4 lg:max-h-[calc(100vh-2rem)] lg:overflow-y-auto">
        {right}
      </aside>
    </div>
  );
}
