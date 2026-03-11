"use client";

/**
 * Sign In with Provider ID — Provider number entry form.
 * Route: /auth/login/provider-id | pageTitle: "Sign In with Provider ID"
 */

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, BadgeCheck, Loader2 } from "lucide-react";
import { AuthLayout } from "@/components/AuthLayout";

export default function ProviderIdLoginPage() {
  const router = useRouter();
  const [providerNumber, setProviderNumber] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setIsSubmitting(true);

    // Store the provider number for the biometric step
    if (typeof window !== "undefined") {
      sessionStorage.setItem("exp:pending_provider_number", providerNumber);
    }

    // Navigate to biometric verification
    router.push("/auth/login/biometric");
  }

  return (
    <AuthLayout>
      <div className="mb-4">
        <Link
          href="/auth/login"
          className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 transition-colors"
        >
          <ArrowLeft className="w-4 h-4" />
          Back to sign in options
        </Link>
      </div>

      <h2 className="text-xl font-semibold text-gray-900 mb-1">Sign In with Provider ID</h2>
      <p className="text-sm text-gray-500 mb-6">
        Enter your registered provider number to continue
      </p>

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label htmlFor="provider-number" className="block text-sm font-medium text-gray-700 mb-1">
            Provider Number
          </label>
          <div className="relative">
            <BadgeCheck className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              id="provider-number"
              type="text"
              required
              value={providerNumber}
              onChange={(e) => setProviderNumber(e.target.value)}
              placeholder="e.g. PRV-2024-00001"
              className="w-full pl-10 pr-4 py-2.5 border border-gray-300 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            />
          </div>
          <p className="mt-1 text-xs text-gray-400">
            Your provider number was assigned during registration
          </p>
        </div>

        <button
          type="submit"
          disabled={isSubmitting || !providerNumber.trim()}
          className="w-full py-2.5 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 transition-colors"
        >
          {isSubmitting ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin" />
              Continuing...
            </>
          ) : (
            "Continue to Biometric"
          )}
        </button>
      </form>

      <div className="mt-6 text-center">
        <Link
          href="/auth/login/email"
          className="text-xs text-blue-600 hover:text-blue-800"
        >
          Sign in with email instead
        </Link>
      </div>
    </AuthLayout>
  );
}
