"use client";

/**
 * AuthLayout — Split-screen authentication layout with Impilo branding.
 * Layout variant: "auth" (used by /auth/* routes)
 *
 * Structure:
 *   [Left panel: Branding (hidden on mobile)]
 *   [Right panel: Auth form content]
 */

import { type ReactNode } from "react";
import Link from "next/link";
import { Heart, Shield, Activity } from "lucide-react";
import { ImpiloBrandLogo } from "@/components/brand/ImpiloBrandLogo";

export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen flex bg-background-deep">
      {/* Left Panel — Branding (desktop only) */}
      <div className="hidden lg:flex lg:w-1/2 bg-gradient-to-br from-impilo-900 via-impilo-800 to-impilo-700 relative overflow-hidden">
        {/* Connected-health watermark */}
        <div
          className="pointer-events-none absolute inset-0 opacity-[0.14]"
          style={{
            backgroundImage: "url('/brand/mark-rgb.svg')",
            backgroundRepeat: "no-repeat",
            backgroundPosition: "right 8% bottom 12%",
            backgroundSize: "min(320px, 45vw)",
            filter: "brightness(0) invert(1)",
          }}
          aria-hidden
        />
        {/* Decorative elements */}
        <div className="absolute inset-0 opacity-10">
          <div className="absolute top-20 left-20 w-64 h-64 rounded-full bg-card/20 blur-3xl" />
          <div className="absolute bottom-20 right-20 w-96 h-96 rounded-full bg-warning/20 blur-3xl" />
        </div>

        <div className="relative z-10 flex flex-col justify-between p-12 text-white">
          <div data-testid="auth-desktop-hero-logo">
            <ImpiloBrandLogo variant="hero" tone="white" />
            <p className="text-sm text-white/85 mt-2">Health Operating System</p>
          </div>

          <div className="space-y-6">
            <div>
              <h2 className="text-3xl font-bold leading-tight">
                One Health OS.
                <br />
                One experience.
              </h2>
              <p className="text-lg text-white/85 mt-3 max-w-md">
                Empowering healthcare providers with seamless, secure,
                and intelligent clinical solutions.
              </p>
            </div>

            <div className="flex items-center gap-6 pt-2">
              <div className="flex items-center gap-2">
                <div className="h-9 w-9 rounded-lg bg-card/15 flex items-center justify-center">
                  <Heart className="h-4 w-4" />
                </div>
                <span className="text-sm">Person-Centered</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="h-9 w-9 rounded-lg bg-card/15 flex items-center justify-center">
                  <Shield className="h-4 w-4" />
                </div>
                <span className="text-sm">Trust-First</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="h-9 w-9 rounded-lg bg-card/15 flex items-center justify-center">
                  <Activity className="h-4 w-4" />
                </div>
                <span className="text-sm">Real-time</span>
              </div>
            </div>
          </div>

          <p className="text-xs text-white/70">
            Impilo Health Operating System
          </p>
        </div>
      </div>

      {/* Right Panel — Auth Content */}
      <div className="flex-1 flex items-center justify-center p-6 lg:p-12 impilo-african-print bg-[color:var(--background)]">
        <div className="w-full max-w-md">
          {/* Mobile header */}
          <div className="lg:hidden text-center mb-8" data-testid="auth-mobile-hero-logo">
            <div className="flex justify-center">
              <ImpiloBrandLogo variant="hero" />
            </div>
            <p className="text-xs text-muted-foreground mt-1">Health Operating System</p>
          </div>

          <div className="bg-card rounded-xl border border-[color:var(--border-strong)] shadow-impilo-floating p-8">{children}</div>

          <div className="mt-4 text-center space-y-2">
            <p className="text-xs text-muted-foreground flex items-center justify-center gap-1.5">
              <Shield className="w-3 h-3" />
              Secure authentication powered by Impilo Trust Layer
            </p>
            <p className="text-xs text-muted-foreground flex items-center justify-center gap-3">
              <Link href="/privacy" className="hover:text-foreground transition-colors">
                Privacy Policy
              </Link>
              <span>&middot;</span>
              <Link href="/terms" className="hover:text-foreground transition-colors">
                Terms of Use
              </Link>
              <span>&middot;</span>
              <Link href="/account-deletion" className="hover:text-foreground transition-colors">
                Account Deletion
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
