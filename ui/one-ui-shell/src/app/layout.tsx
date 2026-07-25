import type { Metadata, Viewport } from "next";
import { Providers } from "@/providers/Providers";
import "@/styles/globals.css";

export const dynamic = "force-dynamic";

export const metadata: Metadata = {
  title: "Impilo — Health Operating System",
  description: "One Health Operating System — Experience Platform",
  manifest: "/manifest.webmanifest",
  icons: {
    icon: [
      { url: "/brand/mark-rgb.svg", type: "image/svg+xml" },
      { url: "/favicon.svg", type: "image/svg+xml" },
    ],
    shortcut: [{ url: "/brand/mark-rgb.svg", type: "image/svg+xml" }],
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
