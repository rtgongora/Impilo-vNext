"use client";

/**
 * Email Login Redirect — Redirects to /auth/login (email is the default login method).
 * Route: /auth/login/email
 */

import { useEffect } from "react";
import { useRouter } from "next/navigation";

export default function EmailLoginRedirectPage() {
  const router = useRouter();

  useEffect(() => {
    router.replace("/auth/login");
  }, [router]);

  return null;
}
