"use client";

/**
 * Finance Layout — Role-based access guard for all /finance/* pages.
 *
 * Only users with SYSTEM_ADMIN, FACILITY_ADMIN, or FINANCE roles
 * can access finance pages. Non-finance authenticated users see an
 * access denied message. Unauthenticated users are redirected to login.
 */

import { type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { ShieldAlert, ArrowLeft, Home } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";

const FINANCE_ROLES = ["SYSTEM_ADMIN", "FACILITY_ADMIN", "FINANCE"];

export default function FinanceLayout({ children }: { children: ReactNode }) {
  const router = useRouter();
  const { isAuthenticated, user, hasRole } = useAuthStore();

  // Redirect unauthenticated users to login
  useEffect(() => {
    if (!isAuthenticated && typeof window !== "undefined") {
      const token = sessionStorage.getItem("exp:auth_token");
      if (!token) {
        router.push("/auth/login");
      }
    }
  }, [isAuthenticated, router]);

  // Check if user has any finance-capable role
  const hasFinanceAccess = FINANCE_ROLES.some((role) => hasRole(role));

  // If authenticated but lacks finance access, show access denied
  if (isAuthenticated && !hasFinanceAccess) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="max-w-md w-full bg-white rounded-xl shadow-lg p-8 text-center">
          <div className="w-16 h-16 rounded-full bg-red-50 flex items-center justify-center mx-auto mb-4">
            <ShieldAlert className="w-8 h-8 text-red-500" />
          </div>
          <h2 className="text-xl font-semibold text-gray-900 mb-2">Access Denied</h2>
          <p className="text-sm text-gray-500 mb-1">
            You do not have permission to access the finance area.
          </p>
          <p className="text-xs text-gray-400 mb-6">
            Required roles: System Admin, Facility Admin, or Finance.
            {user && (
              <span className="block mt-1">
                Your roles: {user.roles.join(", ") || "none"}
              </span>
            )}
          </p>
          <div className="flex gap-3 justify-center">
            <button
              onClick={() => router.back()}
              className="inline-flex items-center gap-1.5 px-4 py-2 text-sm text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200 transition-colors"
            >
              <ArrowLeft className="w-4 h-4" /> Go Back
            </button>
            <button
              onClick={() => router.push("/home")}
              className="inline-flex items-center gap-1.5 px-4 py-2 text-sm text-white bg-impilo-500 rounded-lg hover:bg-impilo-600 transition-colors"
            >
              <Home className="w-4 h-4" /> Home
            </button>
          </div>
        </div>
      </div>
    );
  }

  // Render finance content (either authorized or still hydrating)
  return <>{children}</>;
}
