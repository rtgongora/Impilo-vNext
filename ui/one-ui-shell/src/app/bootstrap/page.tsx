"use client";

import { BootstrapPage } from "@/components/administration-governance/BootstrapPage";
import { useBootstrapStatus } from "@/hooks/useBootstrap";

export default function Page() {
  useBootstrapStatus();
  return <BootstrapPage />;
}
