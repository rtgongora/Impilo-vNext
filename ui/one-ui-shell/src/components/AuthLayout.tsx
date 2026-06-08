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
    <div className="min-h-screen flex bg-gray-50">
      {/* Left Panel — Branding (desktop only) */}
      <div className="hidden lg:flex lg:w-1/2 bg-gradient-to-br from-impilo-500 via-impilo-600 to-impilo-800 relative overflow-hidden">
        {/* Decorative elements */}
        <div className="absolute inset-0 opacity-10">
          <div className="absolute top-20 left-20 w-64 h-64 rounded-full bg-white/20 blur-3xl" />
          <div className="absolute bottom-20 right-20 w-96 h-96 rounded-full bg-brand-yellow/20 blur-3xl" />
        </div>

        <div className="relative z-10 flex flex-col justify-between p-12 text-white">
          <div>
            <ImpiloBrandLogo variant="hero" tone="white" />
            <p className="text-sm text-impilo-100 mt-2">Health Operating System</p>
          </div>

          <div className="space-y-6">
            <div>
              <h2 className="text-3xl font-bold leading-tight">
                One Health OS.
                <br />
                One experience.
              </h2>
              <p className="text-lg text-impilo-100 mt-3 max-w-md">
                Empowering healthcare providers with seamless, secure,
                and intelligent clinical solutions.
              </p>
            </div>

            <div className="flex items-center gap-6 pt-2">
              <div className="flex items-center gap-2">
                <div className="h-9 w-9 rounded-lg bg-white/15 flex items-center justify-center">
                  <Heart className="h-4 w-4" />
                </div>
                <span className="text-sm">Person-Centered</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="h-9 w-9 rounded-lg bg-white/15 flex items-center justify-center">
                  <Shield className="h-4 w-4" />
                </div>
                <span className="text-sm">Trust-First</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="h-9 w-9 rounded-lg bg-white/15 flex items-center justify-center">
                  <Activity className="h-4 w-4" />
                </div>
                <span className="text-sm">Real-time</span>
              </div>
            </div>
          </div>

          <p className="text-xs text-impilo-200">
            Impilo Health Operating System
          </p>
        </div>
      </div>

      {/* Right Panel — Auth Content */}
      <div className="flex-1 flex items-center justify-center p-6 lg:p-12">
        <div className="w-full max-w-md">
          {/* Mobile header */}
          <div className="lg:hidden text-center mb-8">
            <div className="flex justify-center">
              <ImpiloBrandLogo variant="hero" />
            </div>
            <p className="text-xs text-gray-500 mt-1">Health Operating System</p>
          </div>

          <div className="bg-white rounded-xl shadow-lg p-8">{children}</div>

          <div className="mt-4 text-center space-y-2">
            <p className="text-xs text-gray-400 flex items-center justify-center gap-1.5">
              <Shield className="w-3 h-3" />
              Secure authentication powered by Impilo Trust Layer
            </p>
            <p className="text-xs text-gray-400 flex items-center justify-center gap-3">
              <Link href="/privacy" className="hover:text-gray-600 transition-colors">
                Privacy Policy
              </Link>
              <span>&middot;</span>
              <Link href="/terms" className="hover:text-gray-600 transition-colors">
                Terms of Use
              </Link>
              <span>&middot;</span>
              <Link href="/account-deletion" className="hover:text-gray-600 transition-colors">
                Account Deletion
              </Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
