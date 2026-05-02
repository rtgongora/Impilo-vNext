import type { Metadata } from "next";
import localFont from "next/font/local";
import { Providers } from "@/providers/Providers";
import "@/styles/globals.css";

const inter = localFont({
  src: "../fonts/InterVariable.woff2",
  variable: "--font-inter",
  display: "swap",
  weight: "100 900",
});

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
    <html lang="en" className={inter.variable}>
      <body className={inter.className}>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
