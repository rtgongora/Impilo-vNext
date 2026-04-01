"use client";

import { usePathname, useRouter } from "next/navigation";
import { type ReactNode, useEffect } from "react";
import { useAuthStore } from "@/hooks/useAuthStore";

export function AuthGuardProvider({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { isAuthenticated, actorType } = useAuthStore();

  useEffect(() => {
    // Skip guard for auth routes
    if (pathname.startsWith("/auth")) {
      return;
    }

    if (!isAuthenticated) {
      router.replace("/auth/login");
      return;
    }

    if (pathname.startsWith("/admin")) {
      if (actorType === "CITIZEN") {
        router.replace("/portal");
      }
    } else if (pathname.startsWith("/portal")) {
      if (actorType !== "CITIZEN") {
        router.replace("/admin/dashboard");
      }
    }
  }, [pathname, isAuthenticated, actorType, router]);

  return <>{children}</>;
}
