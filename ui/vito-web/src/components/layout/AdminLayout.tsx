"use client";

import { Box, Toolbar, useMediaQuery, useTheme } from "@mui/material";
import { useState, ReactNode } from "react";
import { AdminTopBar } from "./AdminTopBar";
import { AdminDrawer } from "./AdminDrawer";

const DRAWER_WIDTH = 240;

export function AdminLayout({ children }: { children: ReactNode }) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("md"));
  const [mobileOpen, setMobileOpen] = useState(false);

  const handleDrawerToggle = () => {
    setMobileOpen(!mobileOpen);
  };

  return (
    <Box sx={{ display: "flex" }}>
      <AdminTopBar onToggleDrawer={handleDrawerToggle} />
      
      <AdminDrawer
        variant={isMobile ? "temporary" : "permanent"}
        open={isMobile ? mobileOpen : true}
        onClose={handleDrawerToggle}
      />

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: 3,
          width: isMobile ? "100%" : `calc(100% - ${DRAWER_WIDTH}px)`,
          minHeight: "100vh",
          bgcolor: "background.default",
        }}
      >
        <Toolbar />
        {children}
      </Box>
    </Box>
  );
}
