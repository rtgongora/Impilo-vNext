"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Box, CircularProgress, Typography, Alert, Container, Button } from "@mui/material";
import { exchangeCode, parseUserFromToken } from "@/lib/oidc";
import { useAuthStore } from "@/hooks/useAuthStore";

export default function AuthCallbackPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const setAuth = useAuthStore((s) => s.setAuth);
  const [error, setError] = useState<string | null>(null);
  const exchangeStarted = useRef(false);

  useEffect(() => {
    // Guard against React StrictMode double-invoke — only run once.
    if (exchangeStarted.current) return;
    exchangeStarted.current = true;

    const code = searchParams.get("code");
    const state = searchParams.get("state");
    const errorParam = searchParams.get("error");
    const errorDesc = searchParams.get("error_description");

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

        sessionStorage.setItem("vito:tenant_id", user.tenantId);

        if (tokens.refresh_token) {
          sessionStorage.setItem("vito:refresh_token", tokens.refresh_token);
        }
        if (tokens.id_token) {
          sessionStorage.setItem("vito:id_token", tokens.id_token);
        }

        if (user.actorType === "CITIZEN") {
          router.replace("/portal");
        } else {
          router.replace("/admin/dashboard");
        }
      })
      .catch((err: Error) => {
        console.error("Token exchange failed", err);
        setError(err.message ?? "Authentication failed. Please try again.");
      });
  }, [router, searchParams, setAuth]);

  if (error) {
    return (
      <Container maxWidth="sm">
        <Box sx={{ mt: 8 }}>
          <Alert
            severity="error"
            variant="filled"
            action={
              <Button color="inherit" size="small" onClick={() => router.replace("/auth/login")}>
                Try Again
              </Button>
            }
          >
            <Typography variant="subtitle2" fontWeight="bold">
              Authentication Error
            </Typography>
            {error}
          </Alert>
        </Box>
      </Container>
    );
  }

  return (
    <Box
      sx={{
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: 2,
      }}
    >
      <CircularProgress />
      <Typography variant="body2" color="text.secondary">
        Completing sign in...
      </Typography>
    </Box>
  );
}
