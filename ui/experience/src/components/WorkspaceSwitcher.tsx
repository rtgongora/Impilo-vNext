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
import { useRouter } from "next/navigation";
import {
  ArrowLeftRight,
  Building2,
  Clock,
  LogOut,
  Play,
  Stethoscope,
  User,
  X,
} from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import { useExperienceEntry } from "@/providers/ExperienceEntryProvider";
import { SafeSwitchWarning } from "@/components/SafeSwitchWarning";

interface WorkspaceSwitcherProps {
  open: boolean;
  onClose: () => void;
}

export function WorkspaceSwitcher({ open, onClose }: WorkspaceSwitcherProps) {
  const router = useRouter();
  const { user, deactivateProvider } = useAuthStore();
  const shift = useShiftStore((s) => s.shift);
  const { facility, workspace, shiftActive, clearEntry } = useExperienceEntry();

  // Pending action awaiting safe-switch confirmation
  const [pendingAction, setPendingAction] = useState<(() => void) | null>(null);

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
      router.push("/facility");
      onClose();
    });
  }

  function handleEndSession() {
    clearEntry();
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
          className="fixed inset-0 z-[55] bg-slate-950/40 backdrop-blur-sm md:bg-transparent"
          onClick={onClose}
          aria-label="Close workspace switcher"
        />
      )}

      {/* Slide-out panel */}
      <div
        className={[
          "fixed inset-y-0 left-0 z-[56] flex w-[320px] flex-col border-r border-slate-200 bg-[linear-gradient(180deg,#0f172a_0%,#111827_100%)] text-slate-200 shadow-2xl transition-transform duration-300 ease-in-out",
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
            className="rounded-xl border border-white/10 p-2 text-slate-400 transition hover:bg-white/5 hover:text-white"
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
            className="group w-full rounded-2xl border border-white/10 bg-white/5 p-4 text-left transition hover:border-impilo-400/30 hover:bg-impilo-500/10"
          >
            <div className="flex items-start gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-sky-500/20">
                <User className="h-5 w-5 text-sky-400" />
              </div>
              <div>
                <p className="text-sm font-semibold text-white">Personal Profile</p>
                <p className="mt-1 text-xs leading-5 text-slate-400">
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
              className="group w-full rounded-2xl border border-white/10 bg-white/5 p-4 text-left transition hover:border-impilo-400/30 hover:bg-impilo-500/10"
            >
              <div className="flex items-start gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-violet-500/20">
                  <Stethoscope className="h-5 w-5 text-violet-400" />
                </div>
                <div>
                  <p className="text-sm font-semibold text-white">Professional Profile</p>
                  <p className="mt-1 text-xs leading-5 text-slate-400">
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
              className="group w-full rounded-2xl border border-white/10 bg-white/5 p-4 text-left transition hover:border-impilo-400/30 hover:bg-impilo-500/10"
            >
              <div className="flex items-start gap-3">
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-emerald-500/20">
                  <Play className="h-5 w-5 text-emerald-400" />
                </div>
                <div>
                  <p className="text-sm font-semibold text-white">Start Work Session</p>
                  <p className="mt-1 text-xs leading-5 text-slate-400">
                    Select facility and role
                  </p>
                </div>
              </div>
            </button>
          )}

          {/* Active Session — only if currently in a work session */}
          {shiftActive && facility && (
            <div className="mt-4">
              <div className="mb-2 flex items-center gap-2 px-1">
                <div className="h-px flex-1 bg-white/10" />
                <span className="text-[10px] font-semibold uppercase tracking-[0.22em] text-slate-500">
                  Active Session
                </span>
                <div className="h-px flex-1 bg-white/10" />
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
                      <p className="mt-0.5 truncate text-xs text-slate-400">
                        {workspace.workspaceType.replace(/_/g, " ")} &middot; {workspace.name}
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
            className="group flex w-full items-center gap-3 rounded-2xl border border-white/10 bg-white/5 px-4 py-3 text-left transition hover:border-red-400/30 hover:bg-red-500/10"
          >
            <LogOut className="h-5 w-5 text-slate-400 transition group-hover:text-red-400" />
            <span className="text-sm font-medium text-slate-300 transition group-hover:text-red-300">
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
