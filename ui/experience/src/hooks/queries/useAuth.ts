/**
 * Experience UI — Auth Query Hooks
 *
 * Login: OIDC PKCE redirect via buildAuthUrl() in oidc.ts — no mutation needed.
 * Logout: clears local session and redirects to Keycloak RP-initiated logout.
 */

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useAuthStore } from "@/hooks/useAuthStore";
import { buildLogoutUrl } from "@/lib/oidc";

export function useLogout() {
  const queryClient = useQueryClient();
  const clearAuth = useAuthStore((s) => s.clearAuth);

  return useMutation<void, unknown, void>({
    mutationFn: async () => {
      clearAuth();
      queryClient.clear();

      const idToken = sessionStorage.getItem("exp:id_token") ?? "";
      sessionStorage.removeItem("exp:id_token");
      sessionStorage.removeItem("exp:refresh_token");
      sessionStorage.removeItem("exp:token_expiry");

      const redirectUri = `${window.location.origin}/auth/login`;
      const params = new URLSearchParams({
        client_id:
          process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID ?? "experience-ui",
        post_logout_redirect_uri: redirectUri,
        ...(idToken ? { id_token_hint: idToken } : {}),
      });
      const keycloakUrl =
        process.env.NEXT_PUBLIC_KEYCLOAK_URL ?? "http://localhost:8080";
      const realm =
        process.env.NEXT_PUBLIC_KEYCLOAK_REALM ?? "impilo";
      window.location.href = `${keycloakUrl}/realms/${realm}/protocol/openid-connect/logout?${params.toString()}`;
    },
  });
}

export { buildLogoutUrl };
