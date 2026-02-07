import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "PCT \u2014 Patient Care Tracker",
  description:
    "Patient care tracking: work sessions, patient sorting, queue management, and facility control tower",
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
