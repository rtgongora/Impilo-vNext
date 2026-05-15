import type { Metadata } from "next";
import { Providers } from "@/providers/Providers";
import "@/styles/globals.css";

export const metadata: Metadata = {
  title: "Impilo — Health Operating System",
  description: "One Health Operating System — Experience Platform",
  icons: { icon: "/brand/mark-rgb.svg" },
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
