import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "ZIBO \u2014 Terminology Admin",
  description:
    "Terminology, definitions, and classification administration: artifacts, packs, mappings, validation, and governance",
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
