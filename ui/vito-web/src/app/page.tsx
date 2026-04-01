"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/hooks/useAuthStore";
import { CircularProgress, Box } from "@mui/material";

export default function RootPage() {
  const { isAuthenticated, actorType } = useAuthStore();
  const router = useRouter();

  useEffect(() => {
    if (!isAuthenticated) {
      // AuthGuardProvider will handle redirect to login
      return;
    }

    if (actorType === "CITIZEN") {
      router.replace("/portal");
    } else {
      router.replace("/admin/dashboard");
    }
  }, [isAuthenticated, actorType, router]);

  return (
    <Box
      sx={{
        display: "flex",
        height: "100vh",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <CircularProgress />
    </Box>
  );
}
