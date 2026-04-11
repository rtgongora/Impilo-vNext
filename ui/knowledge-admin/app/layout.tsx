import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Knowledge governance — Impilo",
  description: "Review proposed EDLIZ extracts and approve into national clinical knowledge.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body style={{ fontFamily: "system-ui", margin: 0, background: "#0f172a", color: "#e2e8f0" }}>{children}</body>
    </html>
  );
}
