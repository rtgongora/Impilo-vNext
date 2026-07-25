import type { ReactNode } from "react";
import { EmergencyHelpButton } from "./EmergencyHelpButton";
import { PublicHeader } from "./PublicHeader";
import { PublicBackBar } from "./PublicBackBar";
import { PublicFooter } from "./PublicFooter";
import { SkipToContent } from "./SkipToContent";
import { ServiceAdvisoryBanner } from "@/components/advisory/ServiceAdvisoryBanner";

/**
 * Public L0 shell — the chrome for unauthenticated, pre-account pages (welcome,
 * find-care, emergency). Carries NO personal/health data and makes no authenticated
 * calls: every link points either to another public page or into the /auth flow.
 *
 * This is the guest entry the Health OS previously lacked (G-CZO-02): an ordinary
 * person can understand what Impilo is and how to get started before signing in.
 */
export function PublicShell({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen overflow-x-clip bg-[linear-gradient(180deg,#f8fafc_0%,#f0fdf4_52%,#f8fafc_100%)] text-slate-900">
      {/* a11y (C8): keyboard/SR users jump past the chrome to the main landmark. */}
      <SkipToContent targetId="main-content" />

      <PublicHeader />

      {/* Continuity: every public sub-page gets Back + Home (renders nothing on /welcome). */}
      <PublicBackBar />

      {/*
        Nompilo Service Advisory — resolved as DATA and mounted once here so it appears across
        public pages. It self-enforces the never-block-care rule: on emergency/find-care/report
        routes it downgrades to a small, dismissible inline notice and never a blocking modal.
      */}
      <ServiceAdvisoryBanner />

      <main id="main-content" className="mx-auto max-w-[90rem] px-4 py-6 pb-24 sm:px-6 sm:py-8 lg:px-8">
        {children}
      </main>

      {/* Doctrine §7: persistent, clearly labelled Emergency Help on every public page. */}
      <EmergencyHelpButton />

      <PublicFooter />
    </div>
  );
}
