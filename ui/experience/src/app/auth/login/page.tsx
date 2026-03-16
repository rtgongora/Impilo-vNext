"use client";

/**
 * Sign In — Primary login page with email/password form.
 * Route: /auth/login | pageTitle: "Sign In"
 */

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { Mail, Lock, Loader2, BadgeCheck, Fingerprint } from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";
import { useLogin } from "@/hooks/queries/useAuth";
import { useAuthStore } from "@/hooks/useAuthStore";

export default function LoginPage() {
  const router = useRouter();
  const login = useLogin();
  const setAuth = useAuthStore((s) => s.setAuth);

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);

    if (!email.trim() || !password.trim()) {
      setError("Please enter both email and password.");
      return;
    }

    login.mutate(
      { email, password },
      {
        onSuccess: (res) => {
          const { token, user } = res.data.attributes;
          setAuth(
            {
              id: user.id,
              email: user.email,
              displayName: user.displayName,
              roles: user.roles,
              actorType: user.actorType as
                | "PROVIDER"
                | "OPERATOR"
                | "CITIZEN"
                | "SYSTEM",
            },
            token,
          );
          router.push("/home");
        },
        onError: () => {
          setError("Invalid email or password. Please try again.");
        },
      },
    );
  }

  return (
    <AuthLayout>
      <h2 className="text-xl font-semibold text-gray-900 mb-1">Sign In</h2>
      <p className="text-sm text-gray-500 mb-6">
        Enter your credentials to access Impilo vNext
      </p>

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-red-50 border border-red-200 text-sm text-red-700">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label
            htmlFor="email"
            className="block text-sm font-medium text-gray-700 mb-1"
          >
            Email Address
          </label>
          <div className="relative">
            <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              id="email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              className="w-full pl-10 pr-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            />
          </div>
        </div>

        <div>
          <label
            htmlFor="password"
            className="block text-sm font-medium text-gray-700 mb-1"
          >
            Password
          </label>
          <div className="relative">
            <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              id="password"
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Enter your password"
              className="w-full pl-10 pr-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            />
          </div>
        </div>

        <button
          type="submit"
          disabled={login.isPending}
          className="w-full py-2.5 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
        >
          {login.isPending ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin" />
              Signing in...
            </>
          ) : (
            "Sign In"
          )}
        </button>
      </form>

      <div className="mt-6 pt-6 border-t border-gray-200">
        <p className="text-xs text-gray-500 text-center mb-3">
          Or sign in with
        </p>
        <div className="flex gap-3">
          <Link
            href="/auth/login/provider-id"
            className="flex-1 flex items-center justify-center gap-2 px-3 py-2 border border-gray-200 rounded-lg text-sm text-gray-700 hover:border-blue-300 hover:bg-blue-50 transition-colors"
          >
            <BadgeCheck className="w-4 h-4" />
            Provider ID
          </Link>
          <Link
            href="/auth/login/biometric"
            className="flex-1 flex items-center justify-center gap-2 px-3 py-2 border border-gray-200 rounded-lg text-sm text-gray-700 hover:border-blue-300 hover:bg-blue-50 transition-colors"
          >
            <Fingerprint className="w-4 h-4" />
            Biometric
          </Link>
        </div>
      </div>

      <div className="mt-4 text-center">
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
