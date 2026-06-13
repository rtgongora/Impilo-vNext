"use client";

/**
 * Admin Layout — Role-based access guard for all /admin/* pages.
 *
 * Only users with SYSTEM_ADMIN, FACILITY_ADMIN, or DEVELOPER roles
 * can access admin pages. Non-admin authenticated users see an
 * access denied message. Unauthenticated users are redirected to login.
 */

import { type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { ShieldAlert, ArrowLeft, Home } from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";

const ADMIN_ROLES = ["SYSTEM_ADMIN", "FACILITY_ADMIN", "DEVELOPER"];

export default function AdminLayout({ children }: { children: ReactNode }) {
  const router = useRouter();
  const { isAuthenticated, user, hasRole } = useAuthStore();

  // Redirect unauthenticated users to login
  useEffect(() => {
    if (!isAuthenticated && typeof window !== "undefined") {
      const hasSessionMarker =
        !!sessionStorage.getItem("exp:auth_user") ||
        document.cookie.includes("exp_has_session=1");
      if (!hasSessionMarker) {
        router.push("/auth/login");
      }
    }
  }, [isAuthenticated, router]);

  // Check if user has any admin role
  const isAdmin = ADMIN_ROLES.some((role) => hasRole(role));

  // If authenticated but not admin, show access denied
  if (isAuthenticated && !isAdmin) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center p-4">
        <div className="max-w-md w-full bg-card rounded-xl shadow-lg p-8 text-center">
          <div className="w-16 h-16 rounded-full bg-danger-soft flex items-center justify-center mx-auto mb-4">
            <ShieldAlert className="w-8 h-8 text-red-500" />
          </div>
          <h2 className="text-xl font-semibold text-foreground mb-2">Access Denied</h2>
          <p className="text-sm text-muted-foreground mb-1">
            You do not have permission to access the administration area.
          </p>
          <p className="text-xs text-muted-foreground mb-6">
            Required roles: System Admin, Facility Admin, or Developer.
            {user && (
              <span className="block mt-1">
                Your roles: {user.roles.join(", ") || "none"}
              </span>
            )}
          </p>
          <div className="flex gap-3 justify-center">
            <button
              onClick={() => router.back()}
              className="inline-flex items-center gap-1.5 px-4 py-2 text-sm text-foreground bg-neutral-100 rounded-lg hover:bg-neutral-100 transition-colors"
            >
              <ArrowLeft className="w-4 h-4" /> Go Back
            </button>
            <button
              onClick={() => router.push("/home")}
              className="inline-flex items-center gap-1.5 px-4 py-2 text-sm text-white bg-primary rounded-lg hover:bg-primary-hover transition-colors"
            >
              <Home className="w-4 h-4" /> Home
            </button>
          </div>
        </div>
      </div>
    );
  }

  // Render admin content (either authenticated admin or still hydrating)
  return <>{children}</>;
}
