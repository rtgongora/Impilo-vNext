import { AuthGuardProvider } from "@/providers/AuthGuardProvider";
import { AdminLayout } from "@/components/layout/AdminLayout";
import { ReactNode } from "react";

export default function Layout({ children }: { children: ReactNode }) {
  return (
    <AuthGuardProvider>
      <AdminLayout>{children}</AdminLayout>
    </AuthGuardProvider>
  );
}
