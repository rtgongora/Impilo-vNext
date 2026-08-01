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
    <div className="min-h-screen flex flex-col bg-slate-900 text-slate-100 selection:bg-emerald-500 selection:text-white" data-testid="auth-trust-scene">
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
            <div className="rounded-[2rem] border border-slate-200/90 bg-white p-6 sm:p-8 shadow-2xl text-slate-900">
              {children}
            </div>

            <div className="mt-4 space-y-2 text-center text-xs text-slate-400">
              <p className="flex items-center justify-center gap-1.5 font-medium">
                <Shield className="h-3.5 w-3.5 text-emerald-400" />
                Protected by Impilo Trust Layer & TSHEPO Policy Engine
              </p>
              <p className="flex items-center justify-center gap-3">
                <Link href="/privacy" className="hover:text-slate-200 underline">
                  Privacy Policy
                </Link>
                <span>&middot;</span>
                <Link href="/terms" className="hover:text-slate-200 underline">
                  Terms of Use
                </Link>
                <span>&middot;</span>
                <Link href="/account-deletion" className="hover:text-slate-200 underline">
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
