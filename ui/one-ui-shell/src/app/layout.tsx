import type { Metadata, Viewport } from "next";
import { Providers } from "@/providers/Providers";
import "@/styles/globals.css";

// Preview blast-radius validation marker (2026-06-20): targeted-deploy frontend-only probe.

export const metadata: Metadata = {
  title: "Impilo — Health Operating System",
  description: "One Health Operating System — Experience Platform",
  manifest: "/manifest.webmanifest",
  icons: {
    // Modern browsers use the scalable SVG mark for the tab/favicon.
    icon: [
      { url: "/brand/mark-rgb.svg", type: "image/svg+xml" },
      { url: "/favicon.svg", type: "image/svg+xml" },
    ],
    shortcut: [{ url: "/brand/mark-rgb.svg", type: "image/svg+xml" }],
    // NOTE: apple-touch-icon requires a raster PNG (iOS ignores SVG). Pending
    // rasteriser tooling — see docs/runbooks/brand-favicon-assets.md.
  },
};

export const viewport: Viewport = {
  themeColor: "#039748",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
