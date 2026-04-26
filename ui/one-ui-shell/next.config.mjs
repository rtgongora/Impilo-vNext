import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  output: "standalone",
  distDir: ".next-build",
  transpilePackages: ["shared-ui"],
  experimental: {
    // Monorepo: trace shared-ui into standalone output (Next 14)
    outputFileTracingRoot: path.join(__dirname, ".."),
  },
  eslint: {
    // Merged Experience tree; align lint cleanup in a follow-up pass.
    ignoreDuringBuilds: true,
  },
  async rewrites() {
    const gateway =
      process.env.NEXT_PUBLIC_API_GATEWAY_URL || "http://localhost:10000";
    const bff =
      process.env.NEXT_PUBLIC_BFF_URL || "http://localhost:8160";
    return [
      {
        source: "/internal/:path*",
        destination: `${bff}/internal/:path*`,
      },
      {
        source: "/api/:path*",
        destination: `${gateway}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
