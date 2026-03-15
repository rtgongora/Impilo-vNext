import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Impilo Support Console — Helpdesk & Incident Management",
  description: "Support agent console for ticket management, incident coordination, and knowledge base",
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
