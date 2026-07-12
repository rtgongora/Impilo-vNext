"use client";

/**
 * AuthLayout — Split-screen authentication layout with Impilo branding.
 * Layout variant: "auth" (used by /auth/* routes)
 *
 * Structure:
 *   [Left panel: Afro-futurist hero (hidden on mobile)]
 *   [Right panel: Auth form content + compact mobile hero]
 */

import { type ReactNode } from "react";
import Link from "next/link";
import { Shield } from "lucide-react";
import { ImpiloBrandLogo } from "@/components/brand/ImpiloBrandLogo";
import { AuthHeroAfroFuturistBackground } from "@/components/auth/AuthHeroAfroFuturistBackground";
import { AuthHeroContent } from "@/components/auth/AuthHeroContent";
import { AuthHeroWatermark } from "@/components/auth/AuthHeroWatermark";

export function AuthLayout({ children, width = "md" }: { children: ReactNode; width?: "md" | "xl" }) {
  return (
    <div className="min-h-screen flex bg-background-deep">
      {/* Left Panel — Afro-futurist hero (desktop / large tablet landscape) */}
      <div className="relative hidden overflow-hidden lg:flex lg:w-1/2 xl:w-[52%]">
        <AuthHeroAfroFuturistBackground />

        <div className="relative z-10 flex w-full flex-col justify-between p-10 text-white xl:p-12">
          <AuthHeroWatermark />
          <div data-testid="auth-desktop-hero-logo">
            <ImpiloBrandLogo variant="hero" tone="white" />
            <p className="mt-2 text-sm text-white/75">Health Operating System</p>
          </div>

          <AuthHeroContent variant="desktop" />

          <p className="text-xs text-white/60">Impilo Health Operating System</p>
        </div>
      </div>

      {/* Right Panel — Auth Content */}
      <div className="flex flex-1 flex-col items-center justify-center p-4 sm:p-6 lg:p-10 xl:p-12 impilo-african-print bg-[color:var(--background)]">
        <div className={width === "xl" ? "w-full max-w-xl" : "w-full max-w-md"}>
          {/* Mobile / tablet hero strip */}
          <div
            className="relative mb-6 overflow-hidden rounded-2xl border border-[color:var(--border-soft)] lg:hidden"
            data-testid="auth-mobile-hero-logo"
          >
            <div className="relative min-h-[11rem] sm:min-h-[12rem]">
              <AuthHeroAfroFuturistBackground />
              <AuthHeroWatermark compact />
              <div className="relative z-10 flex flex-col gap-4 p-5 sm:p-6">
                <div className="flex justify-center">
                  <ImpiloBrandLogo variant="hero" tone="white" />
                </div>
                <AuthHeroContent variant="compact" />
              </div>
            </div>
          </div>

          <div className="rounded-xl border border-[color:var(--border-strong)] bg-card p-6 shadow-impilo-floating sm:p-8">
            {children}
          </div>

          <div className="mt-4 space-y-2 text-center">
            <p className="flex items-center justify-center gap-1.5 text-xs text-muted-foreground">
              <Shield className="h-3 w-3" />
              Secure authentication powered by Impilo Trust Layer
            </p>
            <p className="flex items-center justify-center gap-3 text-xs text-muted-foreground">
              <Link href="/privacy" className="transition-colors hover:text-foreground">
                Privacy Policy
              </Link>
              <span>&middot;</span>
              <Link href="/terms" className="transition-colors hover:text-foreground">
                Terms of Use
              </Link>
              <span>&middot;</span>
              <Link href="/account-deletion" className="transition-colors hover:text-foreground">
                Account Deletion
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
