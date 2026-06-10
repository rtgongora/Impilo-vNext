"use client";

import { usePathname } from "next/navigation";
import { inferServiceSlugFromPath } from "@/config/serviceBranding";

/** Resolves explicit service slug or infers from the current route. */
export function useInferredServiceSlug(explicit?: string): string | undefined {
  const pathname = usePathname();
  return explicit ?? inferServiceSlugFromPath(pathname);
}
