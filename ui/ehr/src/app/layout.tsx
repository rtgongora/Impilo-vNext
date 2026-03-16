import type { Metadata } from "next";
import "./globals.css";
import { EhrProviders } from "./providers";

export const metadata: Metadata = {
  title: "Impilo EHR",
  description: "Electronic Health Records — Impilo Health Platform",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>
        <EhrProviders>{children}</EhrProviders>
      </body>
    </html>
  );
}
