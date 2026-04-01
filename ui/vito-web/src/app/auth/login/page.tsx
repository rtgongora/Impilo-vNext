"use client";

import { useEffect, useState } from "react";
import { Box, CircularProgress, Typography, Alert, Container } from "@mui/material";
import { buildAuthUrl } from "@/lib/oidc";

/**
 * Login Page — Triggers OIDC Authorization Code + PKCE flow.
 * 
 * This page automatically redirects to Keycloak on mount.
 */
export default function LoginPage() {
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function startLogin() {
      try {
        const redirectUri = `${window.location.origin}/auth/callback`;
        const url = await buildAuthUrl(redirectUri);
        window.location.replace(url);
      } catch (err) {
        console.error("Failed to build auth URL", err);
        setError(err instanceof Error ? err.message : "Failed to initialize login flow");
      }
    }

    startLogin();
  }, []);

  if (error) {
    return (
      <Container maxWidth="sm">
        <Box sx={{ mt: 8 }}>
          <Alert severity="error" variant="filled">
            <Typography variant="subtitle2" fontWeight="bold">
              Login Initialization Error
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
        Redirecting to Identity Provider...
      </Typography>
    </Box>
  );
}
