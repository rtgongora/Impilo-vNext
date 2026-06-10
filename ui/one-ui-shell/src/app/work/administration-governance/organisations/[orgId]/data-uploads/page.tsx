"use client";

import { ScopedAdministrationSurface } from "@/components/administration-governance/ScopedAdministrationSurface";

export default function Page() {
  return <ScopedAdministrationSurface surfaceId="organisations" backHref="/work/administration-governance/organisations" />;
}
