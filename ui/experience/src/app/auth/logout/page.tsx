"use client";

/**
 * Logout — Clears session and redirects to login.
 * Route: /auth/logout | pageTitle: "Signing Out"
 */

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2 } from "lucide-react";
import { MinimalLayout } from "@/components/MinimalLayout";
import { useLogout } from "@/hooks/queries/useAuth";
import { useAuthStore } from "@/hooks/useAuthStore";

export default function LogoutPage() {
  const router = useRouter();
  const logout = useLogout();
  const clearAuth = useAuthStore((s) => s.clearAuth);
  const [hasTriggered, setHasTriggered] = useState(false);

  useEffect(() => {
    if (hasTriggered) return;
    setHasTriggered(true);

    logout.mutate(undefined, {
      onSettled: () => {
        // Always clear auth state regardless of API response
        clearAuth();

        // Clear all session storage
        if (typeof window !== "undefined") {
          sessionStorage.clear();
        }

        router.replace("/auth/login");
      },
    });
  }, [hasTriggered, logout, clearAuth, router]);

  return (
    <MinimalLayout>
      <div className="min-h-screen flex flex-col items-center justify-center">
        <Loader2 className="w-8 h-8 text-blue-600 animate-spin mb-4" />
        <h2 className="text-lg font-medium text-gray-900 mb-1">
          Signing Out
        </h2>
        <p className="text-sm text-gray-500">
          You are being signed out securely...
        </p>
      </div>
    </MinimalLayout>
  );
}
