"use client";

import Link from "next/link";
import { LogOut, UserCircle } from "lucide-react";
import { ShellNotificationTray } from "./ShellNotificationTray";
import { ShellSystemClock } from "./ShellSystemClock";
import { LanguageSwitcher } from "@/components/i18n/LanguageSwitcher";

/**
 * Global account and alert controls for the uppermost app strip.
 * Alerts, live notification tray, profile, and sign-out live here — not on the bottom dock.
 */
export function ShellTopAccountActions() {
  return (
    <div
      className="flex shrink-0 items-center gap-0.5 sm:gap-1"
      data-testid="shell-top-account-actions"
    >
      <ShellSystemClock />
      <LanguageSwitcher variant="chrome" className="hidden sm:inline-flex" />
      <Link
        href="/home/notifications"
        className="hidden items-center gap-1 rounded-lg px-2 py-1 text-xs font-medium text-[color:var(--text-muted)] transition hover:bg-[color:var(--surface-soft)] hover:text-[color:var(--text-primary)] sm:inline-flex"
        title="Notifications"
      >
        Alerts
      </Link>
      <span className="flex items-center rounded-lg border border-[color:var(--border-soft)] bg-[color:var(--surface)]/80 px-0.5 py-0.5 shadow-sm">
        <ShellNotificationTray />
      </span>
      <Link
        href="/home/profile"
        className="inline-flex h-8 items-center gap-1 rounded-lg border border-transparent px-2 transition hover:bg-[color:var(--surface-soft)]"
        title="Profile and settings"
        aria-label="Profile"
      >
        <UserCircle className="h-5 w-5 text-[color:var(--text-muted)]" />
        <span className="hidden text-xs font-medium text-[color:var(--text-primary)] lg:inline">Profile</span>
      </Link>
      <Link
        href="/auth/logout"
        className="inline-flex h-8 items-center gap-1 rounded-lg border border-transparent px-2 transition hover:bg-[color:var(--surface-soft)]"
        title="Sign out"
        aria-label="Sign out"
      >
        <LogOut className="h-4 w-4 text-[color:var(--text-muted)]" />
        <span className="hidden text-xs font-medium text-[color:var(--text-primary)] xl:inline">Sign out</span>
      </Link>
    </div>
  );
}
