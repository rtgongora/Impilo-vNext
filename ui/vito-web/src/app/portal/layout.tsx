import { AuthGuardProvider } from "@/providers/AuthGuardProvider";
import { PortalLayout } from "@/components/layout/PortalLayout";
import { type ReactNode } from "react";

export default function PortalRootLayout({ children }: { children: ReactNode }) {
  return (
    <AuthGuardProvider>
      <PortalLayout>{children}</PortalLayout>
    </AuthGuardProvider>
  );
}
