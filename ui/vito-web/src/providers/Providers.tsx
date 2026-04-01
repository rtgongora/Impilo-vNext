"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type ReactNode, useEffect, useState } from "react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { AuthGuardProvider } from "./AuthGuardProvider";
import { VITO_STORAGE_KEYS } from "@/lib/constants";

function StoreHydrator({ children }: { children: ReactNode }) {
  const [hydrated, setHydrated] = useState(false);
  const { setAuth } = useAuthStore();

  useEffect(() => {
    if (typeof window === "undefined") return;

    try {
      const token = sessionStorage.getItem(VITO_STORAGE_KEYS.AUTH_TOKEN);
      const userStr = sessionStorage.getItem(VITO_STORAGE_KEYS.AUTH_USER);
      if (token && userStr) {
        const user = JSON.parse(userStr);
        setAuth(user, token);
      }
    } catch {
      // Silently ignore parse errors in stored state
    }

    setHydrated(true);
  }, [setAuth]);

  if (!hydrated) {
    return (
      <div style={{ display: "flex", height: "100vh", alignItems: "center", justifyContent: "center" }}>
        <div style={{ color: "#9ca3af", fontSize: "0.875rem" }}>Loading...</div>
      </div>
    );
  }

  return <>{children}</>;
}

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            retry: 1,
          },
        },
      })
  );

  return (
    <QueryClientProvider client={queryClient}>
      <StoreHydrator>
        <AuthGuardProvider>{children}</AuthGuardProvider>
      </StoreHydrator>
    </QueryClientProvider>
  );
}
