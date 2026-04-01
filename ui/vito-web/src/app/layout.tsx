import type { Metadata } from "next";
import { ThemeRegistry } from "@/components/theme/ThemeRegistry";
import { Providers } from "@/providers/Providers";

export const metadata: Metadata = {
  title: "VITO — Impilo Identity Registry",
  description: "Unified Identity Management Platform",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>
        <ThemeRegistry>
          <Providers>{children}</Providers>
        </ThemeRegistry>
      </body>
    </html>
  );
}
