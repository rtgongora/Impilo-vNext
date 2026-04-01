"use client";

import { useAuthStore } from "@/hooks/useAuthStore";
import {
  AppBar,
  Box,
  Button,
  Container,
  Tab,
  Tabs,
  Toolbar,
  Typography,
} from "@mui/material";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { type SyntheticEvent } from "react";

const NAV_LINKS = [
  { label: "Profile", path: "/portal" },
  { label: "My QR", path: "/portal/qr" },
  { label: "Request ID", path: "/portal/request-id" },
  { label: "Recovery", path: "/portal/recovery" },
  { label: "Delegated Pickup", path: "/portal/pickup" },
];

export function PortalTopNav() {
  const pathname = usePathname();
  const router = useRouter();
  const { user, clearAuth } = useAuthStore();

  const handleLogout = () => {
    clearAuth();
    router.replace("/auth/login");
  };

  const activeTab = NAV_LINKS.findIndex((link) => link.path === pathname);

  return (
    <AppBar position="static" color="default" elevation={1}>
      <Container maxWidth="md">
        <Toolbar disableGutters>
          <Typography
            variant="h6"
            component="div"
            sx={{ flexGrow: 1, fontWeight: "bold" }}
          >
            VITO — Citizen Portal
          </Typography>
          <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
            <Typography variant="body2" color="text.secondary">
              {user?.displayName}
            </Typography>
            <Button color="inherit" onClick={handleLogout}>
              Logout
            </Button>
          </Box>
        </Toolbar>
        <Box sx={{ borderBottom: 1, borderColor: "divider" }}>
          <Tabs
            value={activeTab === -1 ? 0 : activeTab}
            variant="scrollable"
            scrollButtons="auto"
          >
            {NAV_LINKS.map((link) => (
              <Tab
                key={link.path}
                label={link.label}
                component={Link}
                href={link.path}
              />
            ))}
          </Tabs>
        </Box>
      </Container>
    </AppBar>
  );
}
