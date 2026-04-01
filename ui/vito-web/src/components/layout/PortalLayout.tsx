import { Container, Box } from "@mui/material";
import { type ReactNode } from "react";
import { PortalTopNav } from "./PortalTopNav";

export function PortalLayout({ children }: { children: ReactNode }) {
  return (
    <Box sx={{ display: "flex", flexDirection: "column", minHeight: "100vh" }}>
      <PortalTopNav />
      <Container
        maxWidth="md"
        sx={{
          py: 4,
          flexGrow: 1,
          display: "flex",
          flexDirection: "column",
        }}
      >
        {children}
      </Container>
    </Box>
  );
}
