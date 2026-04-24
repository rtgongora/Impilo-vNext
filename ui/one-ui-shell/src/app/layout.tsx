import type { Metadata } from "next";
import type { ReactNode } from "react";

import { SessionHydrator } from "@/components/SessionHydrator";

import "./globals.css";

export const metadata: Metadata = {
  title: "Impilo — One UI Shell",
  description: "Shared shell runtime for Impilo vNext",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen font-sans antialiased">
        <SessionHydrator />
        {children}
      </body>
    </html>
  );
}
