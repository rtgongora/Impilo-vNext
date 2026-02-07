import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "BUTANO \u2014 SHR Operations Console",
  description: "Shared Health Record operations: timelines, IPS, reconciliation, and resource statistics",
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
