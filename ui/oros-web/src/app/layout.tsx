import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "OROS \u2014 Orders & Results",
  description:
    "Orders and results orchestration: worklists, order lifecycle, result reporting, reconciliation, catalog, and capability management",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-neutral-50 text-neutral-900 antialiased">
        {children}
      </body>
    </html>
  );
}
