import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "MSIKA Core — Products & Services Registry",
  description: "National registry of health products, services, orderables, chargeables, and capabilities",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>
        <nav className="bg-primary text-white px-6 py-3 flex items-center justify-between shadow">
          <div className="flex items-center gap-6">
            <h1 className="text-lg font-bold tracking-tight">MSIKA Core</h1>
            <a href="/catalogs" className="hover:text-teal-200 text-sm">Catalogs</a>
            <a href="/items" className="hover:text-teal-200 text-sm">Items</a>
            <a href="/import" className="hover:text-teal-200 text-sm">Import</a>
            <a href="/mappings" className="hover:text-teal-200 text-sm">Mappings</a>
            <a href="/publish" className="hover:text-teal-200 text-sm">Publish</a>
            <a href="/browse" className="hover:text-teal-200 text-sm">Browse</a>
          </div>
          <span className="text-xs opacity-70">Products &amp; Services Registry</span>
        </nav>
        <main className="max-w-7xl mx-auto px-4 py-6">{children}</main>
      </body>
    </html>
  );
}
