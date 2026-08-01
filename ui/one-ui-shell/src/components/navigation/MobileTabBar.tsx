"use client";

/**
 * MobileTabBar — the primary navigation for phone-sized viewports (`lg:hidden`).
 *
 * <p>On desktop the shell uses the {@link NavRail} + floating {@code ShellTaskbar} dock —
 * a windowed, power-user metaphor suited to a clinical workstation. That dock does not
 * translate to a phone, so under the `lg` breakpoint it is hidden and replaced by this
 * thumb-reachable bottom tab bar (top app-bar + bottom tabs, the pattern consumer apps use).
 * Destinations are the citizen's most-common jobs; everything else lives one tap away behind
 * the “More” tab, which opens the work-zones drawer (same {@code toggleNavDrawer} the desktop
 * chrome uses), so nothing is lost — only re-prioritised for the device.</p>
 */

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Compass, Home, Menu, Sparkles, Wallet, type LucideIcon } from "lucide-react";
import { useShellStore } from "@/hooks/useShellStore";
import { useAssistantUiStore } from "@/hooks/useAssistantUiStore";

interface TabDef {
  key: string;
  label: string;
  icon: LucideIcon;
  href?: string;
  /** Emphasised centre action (e.g. the Nompilo assistant). */
  emphasize?: boolean;
  /** True when the current path should light this tab up. */
  isActive: (path: string) => boolean;
}

const TABS: TabDef[] = [
  { key: "home", label: "Home", icon: Home, href: "/home", isActive: (p) => p === "/home" || p.startsWith("/home/") || p === "/social" },
  { key: "services", label: "Services", icon: Compass, href: "/discover", isActive: (p) => p.startsWith("/discover") || p.startsWith("/citizen/services") },
  { key: "ask", label: "Nompilo", icon: Sparkles, href: "/ask", emphasize: true, isActive: (p) => p.startsWith("/ask") },
  { key: "wallet", label: "Wallet", icon: Wallet, href: "/wallet", isActive: (p) => p.startsWith("/wallet") },
  { key: "more", label: "More", icon: Menu, isActive: () => false },
];

export function MobileTabBar() {
  const pathname = usePathname() ?? "/home";
  const toggleNavDrawer = useShellStore((s) => s.toggleNavDrawer);
  const setAssistantPanelOpen = useAssistantUiStore((s) => s.setPanelOpen);
  const setAssistantChatOpen = useAssistantUiStore((s) => s.setChatOpen);

  return (
    <nav
      aria-label="Primary"
      className="fixed inset-x-0 bottom-0 z-[9998] flex items-stretch justify-around border-t border-[color:var(--border-soft)] bg-[color:var(--surface)]/95 backdrop-blur-xl lg:hidden"
      style={{ paddingBottom: "env(safe-area-inset-bottom, 0px)" }}
    >
      {TABS.map((tab) => {
        const active = tab.isActive(pathname);
        const Icon = tab.icon;
        const inner = (
          <span className="flex flex-1 flex-col items-center justify-center gap-0.5 py-1.5">
            <span
              className={[
                "flex h-8 w-8 items-center justify-center rounded-xl transition",
                tab.emphasize
                  ? "bg-[color:var(--primary)] text-[color:var(--on-primary,#fff)] shadow-impilo-floating"
                  : active
                    ? "bg-[color:var(--primary-soft)]/80 text-[color:var(--primary-hover)]"
                    : "text-[color:var(--text-secondary)]",
              ].join(" ")}
            >
              <Icon className="h-5 w-5" aria-hidden />
            </span>
            <span
              className={[
                "text-[10px] font-medium leading-none",
                active || tab.emphasize ? "text-[color:var(--text-primary)]" : "text-[color:var(--text-secondary)]",
              ].join(" ")}
            >
              {tab.label}
            </span>
          </span>
        );

        if (tab.key === "ask") {
          return (
            <button
              key={tab.key}
              type="button"
              onClick={() => {
                setAssistantPanelOpen(true);
                setAssistantChatOpen(true);
              }}
              aria-label="Open Nompilo assistant"
              className="flex min-h-[56px] flex-1 items-stretch text-center outline-none focus-visible:ring-2 focus-visible:ring-[color:var(--primary)]"
            >
              {inner}
            </button>
          );
        }
        if (tab.href) {
          return (
            <Link
              key={tab.key}
              href={tab.href}
              aria-current={active ? "page" : undefined}
              className="flex min-h-[56px] flex-1 items-stretch text-center outline-none focus-visible:ring-2 focus-visible:ring-[color:var(--primary)]"
            >
              {inner}
            </Link>
          );
        }
        return (
          <button
            key={tab.key}
            type="button"
            onClick={() => toggleNavDrawer()}
            aria-label="More — open all zones and tools"
            className="flex min-h-[56px] flex-1 items-stretch text-center outline-none focus-visible:ring-2 focus-visible:ring-[color:var(--primary)]"
          >
            {inner}
          </button>
        );
      })}
    </nav>
  );
}
