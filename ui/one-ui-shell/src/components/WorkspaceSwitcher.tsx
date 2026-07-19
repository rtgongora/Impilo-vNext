"use client";

/**
 * Workspace Switcher (Screen 15)
 *
 * Slide-out panel that allows the user to switch between:
 * - Personal Profile (citizen mode)
 * - Professional Profile (if provider activated)
 * - Start/switch work session (if provider activated)
 * - View and end the current active work session
 * - Sign out
 *
 * Health OS Doctrine: "One person anchor, many roles, many contexts,
 * many experience modes, one governed runtime."
 *
 * Triggered from the sidebar via a context-switch button.
 * Works in both expanded and collapsed sidebar states.
 */

import { useCallback, useState } from "react";
import {
  ArrowLeftRight,
  Building2,
  Clock,
  Focus,
  LogOut,
  Play,
  Stethoscope,
  User,
  X,
} from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useOperationalContextStore } from "@/hooks/useOperationalContextStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import { useExperienceEntry } from "@/providers/ExperienceEntryProvider";
import { SafeSwitchWarning } from "@/components/SafeSwitchWarning";

/** Fallback router using window.location for environments without Next.js navigation context. */
const fallbackRouter = {
  push: (path: string) => {
    if (typeof window !== "undefined") {
      window.location.href = path;
    }
  },
};

/**
 * Safely obtain a Next.js router instance. Falls back to a thin
 * wrapper around window.location when the Next.js navigation
 * context is unavailable (e.g. in unit tests without full providers).
 */
function useSafeRouter(): { push: (path: string) => void } {
  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const nav = require("next/navigation");
    const r = nav.useRouter();
    if (r && typeof r.push === "function") return r;
  } catch {
    // Swallow — fall through to window.location fallback
  }
  return fallbackRouter;
}

interface WorkspaceSwitcherProps {
  open: boolean;
  onClose: () => void;
}

export function WorkspaceSwitcher({ open, onClose }: WorkspaceSwitcherProps) {
  const router = useSafeRouter();
  const { user, deactivateProvider } = useAuthStore();
  const shift = useShiftStore((s) => s.shift);
  const { facility, workspace, shiftActive, clearEntry } = useExperienceEntry();
  const focusedWorkMode = useOperationalContextStore((s) => s.focusedWorkMode);
  const setFocusedWorkMode = useOperationalContextStore((s) => s.setFocusedWorkMode);

  // Pending action awaiting safe-switch confirmation
  const [pendingAction, setPendingAction] = useState<(() => void) | null>(null);
  const [confirmExitFocused, setConfirmExitFocused] = useState(false);

  const providerActivated = user?.providerActivated ?? false;

  /**
   * Guard: if a shift is active, show the SafeSwitchWarning
   * before executing the action. Otherwise, run it immediately.
   */
  const guardedSwitch = useCallback(
    (action: () => void) => {
      if (shiftActive) {
        setPendingAction(() => action);
      } else {
        action();
      }
    },
    [shiftActive],
  );

  const handleConfirmSwitch = useCallback(() => {
    clearEntry();
    if (pendingAction) {
      pendingAction();
    }
    setPendingAction(null);
  }, [clearEntry, pendingAction]);

  const handleCancelSwitch = useCallback(() => {
    setPendingAction(null);
  }, []);

  function handlePersonalProfile() {
    guardedSwitch(() => {
      if (providerActivated) {
        deactivateProvider();
      }
      router.push("/home");
      onClose();
    });
  }

  function handleProfessionalProfile() {
    guardedSwitch(() => {
      router.push("/home/credentials");
      onClose();
    });
  }

  function handleStartWorkSession() {
    guardedSwitch(() => {
      // D-P3/PJ5: the workplace hub proves the chosen assignment against
      // Vashandi and mints the duty-scoped WORK_CONTEXT token (real work
      // session), rather than only setting local facility/workspace state.
      router.push("/provider/workplace");
      onClose();
    });
  }

  function handleEndSession() {
    clearEntry();
    router.push("/home");
    onClose();
  }

  function handleEnterFocusedWorkMode() {
    guardedSwitch(() => {
      setFocusedWorkMode(true);
      router.push("/home");
      onClose();
    });
  }

  function handleRequestExitFocusedWorkMode() {
    setConfirmExitFocused(true);
  }

  function handleConfirmExitFocusedWorkMode() {
    setFocusedWorkMode(false);
    setConfirmExitFocused(false);
    router.push("/home");
    onClose();
  }

  function handleSignOut() {
    guardedSwitch(() => {
      router.push("/auth/logout");
      onClose();
    });
  }

  const shiftStartTime = shift?.startedAt
    ? new Date(shift.startedAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
    : null;

  return (
    <>
      {/* Backdrop */}
      {open && (
        <button
          type="button"
          className="fixed inset-0 z-[55] bg-card/40 backdrop-blur-sm md:bg-transparent"
          onClick={onClose}
          aria-label="Close workspace switcher"
        />
      )}

      {/* Slide-out panel */}
      <div
        className={[
          "fixed inset-y-0 left-0 z-[56] flex w-[320px] flex-col border-r border-border bg-[linear-gradient(180deg,#0f172a_0%,#111827_100%)] text-slate-200 shadow-2xl transition-transform duration-300 ease-in-out",
          open ? "translate-x-0" : "-translate-x-full",
        ].join(" ")}
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-white/10 px-5 py-5">
          <div className="flex items-center gap-3">
            <ArrowLeftRight className="h-5 w-5 text-impilo-400" />
            <h2 className="text-base font-semibold text-white">Switch Context</h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-xl border border-white/10 p-2 text-muted-foreground transition hover:bg-card/5 hover:text-white"
            aria-label="Close"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Options */}
        <div className="flex-1 overflow-y-auto px-4 py-5 space-y-2">
          {/* Personal Profile — always visible */}
          <button
            type="button"
            onClick={handlePersonalProfile}
            className="group w-full rounded-2xl border border-white/10 bg-card/5 p-4 text-left transition hover:border-impilo-400/30 hover:bg-primary/10"
          >
            <div className="flex items-start gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-sky-500/20">
                <User className="h-5 w-5 text-sky-400" />
              </div>
              <div>
                <p className="text-sm font-semibold text-white">Personal Profile</p>
                <p className="mt-1 text-xs leading-5 text-muted-foreground">
                  Your health, wellness, and personal services
                </p>
              </div>
            </div>
          </button>

          {/* Professional Profile — only if provider activated */}
          {providerActivated && (
            <button
              type="button"
              onClick={handleProfessionalProfile}
              className="group w-full rounded-2xl border border-white/10 bg-card/5 p-4 text-left transition hover:border-impilo-400/30 hover:bg-primary/10"
            >
              <div className="flex items-start gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-violet-500/20">
                  <Stethoscope className="h-5 w-5 text-violet-400" />
                </div>
                <div>
                  <p className="text-sm font-semibold text-white">Professional Profile</p>
                  <p className="mt-1 text-xs leading-5 text-muted-foreground">
                    Registration, licences, affiliations
                  </p>
                </div>
              </div>
            </button>
          )}

          {/* Start Work Session — only if provider activated */}
          {providerActivated && (
            <button
              type="button"
              onClick={handleStartWorkSession}
              className="group w-full rounded-2xl border border-white/10 bg-card/5 p-4 text-left transition hover:border-impilo-400/30 hover:bg-primary/10"
            >
              <div className="flex items-start gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-emerald-500/20">
                  <Play className="h-5 w-5 text-emerald-400" />
                </div>
                <div>
                  <p className="text-sm font-semibold text-white">Start Work Session</p>
                  <p className="mt-1 text-xs leading-5 text-muted-foreground">
                    Select facility and role
                  </p>
                </div>
              </div>
            </button>
          )}

          {/* Focused work mode — professional execution without personal/pro tabs */}
          {providerActivated && (
            <div className="mt-2">
              {focusedWorkMode ? (
                <div className="rounded-2xl border border-amber-400/30 bg-amber-400/10 p-4">
                  <div className="flex items-start gap-3">
                    <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-amber-500/20">
                      <Focus className="h-5 w-5 text-amber-300" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-semibold text-white">Focused work mode</p>
                      <p className="mt-1 text-xs leading-5 text-muted-foreground">
                        Personal and professional tabs are hidden to reduce accidental context switches.
                      </p>
                    </div>
                  </div>
                  {confirmExitFocused ? (
                    <div className="mt-4 space-y-2">
                      <p className="text-xs text-amber-100">Exit focused work mode and show all tabs?</p>
                      <div className="flex gap-2">
                        <button
                          type="button"
                          onClick={handleConfirmExitFocusedWorkMode}
                          className="flex-1 rounded-xl bg-amber-500/90 px-3 py-2.5 text-sm font-semibold text-slate-900"
                        >
                          Yes, exit
                        </button>
                        <button
                          type="button"
                          onClick={() => setConfirmExitFocused(false)}
                          className="flex-1 rounded-xl border border-white/20 px-3 py-2.5 text-sm font-medium text-white"
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                  ) : (
                    <button
                      type="button"
                      onClick={handleRequestExitFocusedWorkMode}
                      className="mt-4 w-full rounded-xl border border-amber-400/40 bg-amber-500/10 px-4 py-2.5 text-sm font-medium text-amber-100 transition hover:bg-amber-500/20"
                    >
                      Exit focused work mode
                    </button>
                  )}
                </div>
              ) : (
                <button
                  type="button"
                  onClick={handleEnterFocusedWorkMode}
                  className="group w-full rounded-2xl border border-white/10 bg-card/5 p-4 text-left transition hover:border-amber-400/30 hover:bg-amber-400/10"
                >
                  <div className="flex items-start gap-3">
                    <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-amber-500/20">
                      <Focus className="h-5 w-5 text-amber-300" />
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-white">Enter focused work mode</p>
                      <p className="mt-1 text-xs leading-5 text-muted-foreground">
                        Hide personal tabs and non-work zones for uninterrupted clinical work
                      </p>
                    </div>
                  </div>
                </button>
              )}
            </div>
          )}

          {/* Active Session — only if currently in a work session */}
          {shiftActive && facility && (
            <div className="mt-4">
              <div className="mb-2 flex items-center gap-2 px-1">
                <div className="h-px flex-1 bg-card/10" />
                <span className="text-[10px] font-semibold uppercase tracking-[0.22em] text-muted-foreground">
                  Active Session
                </span>
                <div className="h-px flex-1 bg-card/10" />
              </div>

              <div className="rounded-2xl border border-emerald-400/20 bg-emerald-400/5 p-4">
                <div className="flex items-start gap-3">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-emerald-500/20">
                    <Building2 className="h-5 w-5 text-emerald-400" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold text-white">
                      {facility.name}
                    </p>
                    {workspace && (
                      <p className="mt-0.5 truncate text-xs text-muted-foreground">
                        {workspace.workspaceType?.replace(/_/g, " ") ?? "Workspace"} &middot; {workspace.name}
                      </p>
                    )}
                    {shiftStartTime && (
                      <p className="mt-1 flex items-center gap-1.5 text-xs text-emerald-300">
                        <Clock className="h-3 w-3" />
                        Since {shiftStartTime}
                      </p>
                    )}
                  </div>
                </div>

                <button
                  type="button"
                  onClick={handleEndSession}
                  className="mt-4 w-full rounded-xl border border-red-400/30 bg-red-500/10 px-4 py-2.5 text-sm font-medium text-red-300 transition hover:bg-red-500/20 hover:text-red-200"
                >
                  End Session
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Footer: Sign Out */}
        <div className="border-t border-white/10 px-4 py-4">
          <button
            type="button"
            onClick={handleSignOut}
            className="group flex w-full items-center gap-3 rounded-2xl border border-white/10 bg-card/5 px-4 py-3 text-left transition hover:border-red-400/30 hover:bg-red-500/10"
          >
            <LogOut className="h-5 w-5 text-muted-foreground transition group-hover:text-red-400" />
            <span className="text-sm font-medium text-muted-foreground transition group-hover:text-red-300">
              Sign Out
            </span>
          </button>
        </div>
      </div>

      {/* Safe Switch Warning modal */}
      <SafeSwitchWarning
        open={pendingAction !== null}
        facilityName={facility?.name ?? "your facility"}
        workspaceName={workspace?.name}
        shiftStartedAt={shift?.startedAt}
        onConfirm={handleConfirmSwitch}
        onCancel={handleCancelSwitch}
      />
    </>
  );
}
