"use client";

/**
 * Sign In — Login hub page with 3 method cards.
 * Route: /auth/login | pageTitle: "Sign In"
 */

import Link from "next/link";
import { AuthLayout } from "@/components/AuthLayout";
import { Mail, BadgeCheck, Fingerprint } from "lucide-react";

const LOGIN_METHODS = [
  {
    title: "Email & Password",
    description: "Sign in with your email address and password",
    href: "/auth/login/email",
    icon: Mail,
  },
  {
    title: "Provider ID",
    description: "Sign in using your registered provider number",
    href: "/auth/login/provider-id",
    icon: BadgeCheck,
  },
  {
    title: "Biometric",
    description: "Sign in with fingerprint or facial recognition",
    href: "/auth/login/biometric",
    icon: Fingerprint,
  },
] as const;

export default function LoginPage() {
  return (
    <AuthLayout>
      <h2 className="text-xl font-semibold text-gray-900 mb-1">Sign In</h2>
      <p className="text-sm text-gray-500 mb-6">
        Choose your preferred authentication method
      </p>

      <div className="space-y-3">
        {LOGIN_METHODS.map((method) => {
          const Icon = method.icon;
          return (
            <Link
              key={method.href}
              href={method.href}
              className="flex items-center gap-4 p-4 rounded-lg border border-gray-200 hover:border-blue-300 hover:bg-blue-50 transition-colors group"
            >
              <div className="w-10 h-10 rounded-lg bg-blue-100 text-blue-600 flex items-center justify-center shrink-0 group-hover:bg-blue-200 transition-colors">
                <Icon className="w-5 h-5" />
              </div>
              <div>
                <div className="font-medium text-gray-900 text-sm">
                  {method.title}
                </div>
                <div className="text-xs text-gray-500">{method.description}</div>
              </div>
            </Link>
          );
        })}
      </div>

      <div className="mt-6 text-center">
        <Link
          href="/auth/forgot-password"
          className="text-xs text-blue-600 hover:text-blue-800"
        >
          Forgot your password?
        </Link>
      </div>
    </AuthLayout>
  );
}
