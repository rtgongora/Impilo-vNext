"use client";

/**
 * Experience UI — Provider Tree
 *
 * Provider tree order (from 05_state_and_storage.md):
 *   QueryClient > Auth > Facility > Workspace > Shift > Router
 *
 * Two sessionStorage keys persist ephemeral state:
 *   - exp:auth_token / exp:auth_user
 *   - exp:facility / exp:workspace / exp:shift
 */

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { type ReactNode, useState } from "react";
import { AuthGuardProvider } from "./AuthGuardProvider";

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
      <AuthGuardProvider>{children}</AuthGuardProvider>
    </QueryClientProvider>
  );
}
