"use client";

/**
 * InactivityLockProvider — Privacy-first session auto-lock.
 *
 * Monitors user activity (mouse, keyboard, touch, scroll) and
 * triggers a privacy lock screen after a configurable period of
 * inactivity. The lock screen hides all PII and requires the
 * user to click "Resume" to continue.
 *
 * If the extended timeout is reached, the session is fully
 * logged out and the user is redirected to login.
 *
 * Per Privacy Policy §14 and Health OS doctrine: "reasonable
 * technical safeguards designed to protect information against
 * unauthorized access."
 */

import { useEffect, useRef, useState, type ReactNode } from "react";
import { useRouter, usePathname } from "next/navigation";
import { Shield, Lock, LogOut } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { usePrivacyDisplayStore } from "@/hooks/usePrivacyDisplayStore";

/** Minutes of inactivity before the lock screen appears. */
const LOCK_TIMEOUT_MINUTES = 5;
/** Minutes after lock screen before full logout. */
const LOGOUT_TIMEOUT_MINUTES = 15;

const ACTIVITY_EVENTS = ["mousemove", "mousedown", "keydown", "touchstart", "scroll"] as const;

/** Paths exempt from inactivity lock (public routes). */
const EXEMPT_PREFIXES = ["/auth", "/privacy", "/terms", "/consent", "/account-deletion", "/kiosk"];

export function InactivityLockProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { isAuthenticated, clearAuth } = useAuthStore();
  const prevLevel = useRef(usePrivacyDisplayStore.getState().level);

  const [locked, setLocked] = useState(false);
  const [logoutCountdown, setLogoutCountdown] = useState(LOGOUT_TIMEOUT_MINUTES * 60);

  const lastActivityRef = useRef(Date.now());
  const lockTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const logoutTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const isExempt = EXEMPT_PREFIXES.some((p) => pathname.startsWith(p));

  // Track user activity
  useEffect(() => {
    if (!isAuthenticated || isExempt) return;

    function onActivity() {
      lastActivityRef.current = Date.now();
      if (locked) return; // Don't reset if already locked
    }

    for (const event of ACTIVITY_EVENTS) {
      window.addEventListener(event, onActivity, { passive: true });
    }

    return () => {
      for (const event of ACTIVITY_EVENTS) {
        window.removeEventListener(event, onActivity);
      }
    };
  }, [isAuthenticated, isExempt, locked]);

  // Check for inactivity → lock
  useEffect(() => {
    if (!isAuthenticated || isExempt || locked) return;

    lockTimerRef.current = setInterval(() => {
      const elapsed = (Date.now() - lastActivityRef.current) / 1000 / 60;
      if (elapsed >= LOCK_TIMEOUT_MINUTES) {
        // Save current privacy level and switch to MINIMAL
        prevLevel.current = usePrivacyDisplayStore.getState().level;
        usePrivacyDisplayStore.getState().setLevel("MINIMAL");
        setLocked(true);
        setLogoutCountdown(LOGOUT_TIMEOUT_MINUTES * 60);
      }
    }, 10_000); // Check every 10 seconds

    return () => {
      if (lockTimerRef.current) clearInterval(lockTimerRef.current);
    };
  }, [isAuthenticated, isExempt, locked]);

  // Countdown to full logout while locked
  useEffect(() => {
    if (!locked) return;

    logoutTimerRef.current = setInterval(() => {
      setLogoutCountdown((prev) => {
        if (prev <= 1) {
          handleLogout();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => {
      if (logoutTimerRef.current) clearInterval(logoutTimerRef.current);
    };
  }, [locked]);

  function handleResume() {
    lastActivityRef.current = Date.now();
    // Restore previous privacy level
    usePrivacyDisplayStore.getState().setLevel(prevLevel.current);
    setLocked(false);
  }

  function handleLogout() {
    clearAuth();
    setLocked(false);
    router.replace("/auth/login?reason=inactivity");
  }

  // Lock screen overlay
  if (locked && isAuthenticated) {
    const minutes = Math.floor(logoutCountdown / 60);
    const seconds = logoutCountdown % 60;

    return (
      <>
        {/* Hidden children — PII is not visible */}
        <div className="hidden">{children}</div>

        {/* Lock screen */}
        <div className="fixed inset-0 z-[9999] bg-gray-900/95 flex items-center justify-center p-4">
          <div className="w-full max-w-sm text-center">
            <div className="w-16 h-16 rounded-full bg-blue-600/20 flex items-center justify-center mx-auto mb-6">
              <Lock className="w-8 h-8 text-blue-400" />
            </div>

            <h2 className="text-xl font-semibold text-white mb-2">
              Session Locked
            </h2>
            <p className="text-sm text-gray-400 mb-6">
              Your session was locked due to inactivity to protect patient privacy.
            </p>

            <button
              onClick={handleResume}
              className="w-full py-3 bg-blue-600 text-white font-medium rounded-lg hover:bg-blue-700 transition-colors flex items-center justify-center gap-2 mb-3"
            >
              <Shield className="w-4 h-4" />
              Resume Session
            </button>

            <button
              onClick={handleLogout}
              className="w-full py-2.5 text-sm text-gray-400 hover:text-gray-200 flex items-center justify-center gap-2 transition-colors"
            >
              <LogOut className="w-4 h-4" />
              Sign Out
            </button>

            <p className="mt-4 text-xs text-gray-500">
              Auto sign-out in {minutes}:{String(seconds).padStart(2, "0")}
            </p>
          </div>
        </div>
      </>
    );
  }

  return <>{children}</>;
}
