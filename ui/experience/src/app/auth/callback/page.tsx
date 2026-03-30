"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Loader2, AlertCircle } from "lucide-react";
import { exchangeCode, parseUserFromToken } from "@/lib/oidc";
import { useAuthStore } from "@/hooks/useAuthStore";

export default function OidcCallbackPage() {
  const router = useRouter();
  const setAuth = useAuthStore((s) => s.setAuth);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const code = params.get("code");
    const state = params.get("state");
    const errorParam = params.get("error");
    const errorDesc = params.get("error_description");

    if (errorParam) {
      setError(errorDesc ?? errorParam);
      return;
    }

    if (!code || !state) {
      setError("Missing OIDC callback parameters.");
      return;
    }

    const redirectUri = `${window.location.origin}/auth/callback`;

    exchangeCode(code, state, redirectUri)
      .then((tokens) => {
        const user = parseUserFromToken(tokens.access_token);
        setAuth(user, tokens.access_token);

        if (tokens.refresh_token) {
          sessionStorage.setItem("exp:refresh_token", tokens.refresh_token);
        }
        if (tokens.id_token) {
          sessionStorage.setItem("exp:id_token", tokens.id_token);
        }

        sessionStorage.setItem(
          "exp:token_expiry",
          String(Date.now() + tokens.expires_in * 1000)
        );

        router.replace("/home");
      })
      .catch((err: Error) => {
        setError(err.message ?? "Authentication failed. Please try again.");
      });
  }, [router, setAuth]);

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="max-w-sm w-full mx-4 p-6 bg-white rounded-xl shadow-sm border border-red-200">
          <div className="flex items-center gap-3 mb-3">
            <AlertCircle className="w-5 h-5 text-red-500 shrink-0" />
            <h2 className="text-sm font-semibold text-gray-900">
              Authentication Failed
            </h2>
          </div>
          <p className="text-sm text-red-700 mb-4">{error}</p>
          <button
            onClick={() => router.replace("/auth/login")}
            className="w-full py-2 text-sm font-medium text-white bg-blue-600 rounded-lg hover:bg-blue-700 transition-colors"
          >
            Back to Sign In
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="flex flex-col items-center gap-3 text-gray-500">
        <Loader2 className="w-8 h-8 animate-spin text-blue-600" />
        <p className="text-sm">Completing sign in…</p>
      </div>
    </div>
  );
}
