"use client";

import { useI18n } from "@/lib/i18n/useI18n";

/**
 * "Skip to main content" link (C8 a11y). Visually hidden until it receives keyboard
 * focus, then pinned top-left so a keyboard / screen-reader user can jump past the
 * chrome straight to <main id="main-content">. Client so the label localises; the
 * `.impilo-skip-link` styles live in globals.css.
 *
 * @param targetId id of the main landmark to jump to (default "main-content").
 */
export function SkipToContent({ targetId = "main-content" }: { targetId?: string }) {
  const { t } = useI18n();
  return (
    <a href={`#${targetId}`} className="impilo-skip-link">
      {t("public.chrome.skipToContent")}
    </a>
  );
}
