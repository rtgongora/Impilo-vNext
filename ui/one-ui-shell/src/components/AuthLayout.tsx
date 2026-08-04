"use client";

/**
 * AuthLayout — Immersive Trust Scene layout integrated into the shared Impilo shell.
 *
 * Implements the doctrine:
 * "Sign-in is not a wall between public Impilo and vNext.
 * It is a temporary trust conversation inside the same living Impilo experience."
 */

import { type ReactNode } from "react";
import Link from "next/link";
import { Shield } from "lucide-react";
import { PublicHeader } from "@/components/public/PublicHeader";
import { PublicFooter } from "@/components/public/PublicFooter";
import { EmergencyHelpButton } from "@/components/public/EmergencyHelpButton";
import { JourneyContextPanel } from "@/components/auth/JourneyContextPanel";

export function AuthLayout({
  children,
  returnTo = null,
}: {
  children: ReactNode;
  returnTo?: string | null;
  width?: "md" | "xl";
}) {
  return (
    <div
      // The trust conversation happens on the deep end of the Impilo canvas: the same
      // teal family as the public hero's floor, with a soft emerald bloom low-left.
      // Trust rises with the action — the ground darkens with it, calmly, not to slate.
      className="relative min-h-screen flex flex-col bg-[radial-gradient(56%_58%_at_-6%_104%,rgba(16,185,160,.28),transparent_66%),linear-gradient(180deg,#0B4A4D_0%,#073540_48%,#03222A_100%)] text-slate-100 selection:bg-emerald-500 selection:text-white"
      data-testid="auth-trust-scene"
    >
      {/* Grain keeps the deep gradient from banding on low-end panels. */}
      <div aria-hidden className="impilo-grain pointer-events-none absolute inset-0 opacity-50" />
      {/* Shared Responsive Public Header (Emergency, Language, Accessibility, Logo) */}
      <PublicHeader />

      {/* Main Trust Canvas */}
      <main className="flex-1 py-8 pb-24 px-4 sm:px-6 lg:px-8 max-w-[90rem] mx-auto w-full flex items-center justify-center">
        <div className="w-full grid grid-cols-1 lg:grid-cols-12 gap-8 items-stretch">
          {/* Left Column (Desktop): Saved Journey Context Panel */}
          <div className="lg:col-span-6 xl:col-span-7 flex flex-col justify-center">
            <JourneyContextPanel returnTo={returnTo} />
          </div>

          {/* Right Column: Interactive Authentication Surface */}
          <div className="lg:col-span-6 xl:col-span-5 flex flex-col justify-center">
            <div className="rounded-[2rem] border border-white/40 bg-white/95 p-6 sm:p-8 text-slate-900 shadow-[0_36px_90px_-30px_rgba(2,30,26,.85)] ring-1 ring-inset ring-white/60 backdrop-blur-md [.low-blur_&]:bg-white [.low-blur_&]:backdrop-blur-none">
              {children}
            </div>

            <div className="mt-4 space-y-2 text-center text-xs text-teal-100/60">
              <p className="flex items-center justify-center gap-1.5 font-medium">
                <Shield className="h-3.5 w-3.5 text-emerald-400" />
                Protected by Impilo Trust Layer & TSHEPO Policy Engine
              </p>
              <p className="flex items-center justify-center gap-3">
                <Link href="/privacy" className="hover:text-white underline">
                  Privacy Policy
                </Link>
                <span>&middot;</span>
                <Link href="/terms" className="hover:text-white underline">
                  Terms of Use
                </Link>
                <span>&middot;</span>
                <Link href="/account-deletion" className="hover:text-white underline">
                  Account Deletion
                </Link>
              </p>
            </div>
          </div>
        </div>
      </main>

      {/* Desktop uses the header action; condensed layouts use the persistent control. */}
      <div className="lg:hidden">
        <EmergencyHelpButton />
      </div>

      {/* Shared Public Footer */}
      <PublicFooter />
    </div>
  );
}
