import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const isWindows = process.platform === "win32";

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  output: isWindows ? undefined : "standalone",
  distDir: ".next-build",
  transpilePackages: ["shared-ui"],
  experimental: {
    // Monorepo: trace shared-ui into standalone output (Next 14)
    outputFileTracingRoot: path.join(__dirname, ".."),
    // Disable client-side router cache for dynamic pages so that
    // pre-auth prefetch redirects (middleware → /auth/login) are not
    // reused after the user logs in and navigates to /home.
    staleTimes: {
      dynamic: 0,
      static: 300,
    },
  },
  eslint: {
    // Merged Experience tree; align lint cleanup in a follow-up pass.
    ignoreDuringBuilds: true,
  },
  webpack: (config) => {
    // DWV package "node" export breaks Next build; force browser bundle.
    config.resolve.alias = {
      ...config.resolve.alias,
      dwv: path.join(__dirname, "../node_modules/dwv/dist/dwv.min.js"),
    };
    return config;
  },
  async rewrites() {
    const gateway =
      process.env.API_GATEWAY_URL ||
      process.env.NEXT_PUBLIC_API_GATEWAY_URL ||
      "http://localhost:10000";
    const bff =
      process.env.BFF_URL ||
      process.env.NEXT_PUBLIC_BFF_URL ||
      "http://localhost:8160";
    return [
      {
        source: "/internal/:path*",
        destination: `${gateway}/internal/:path*`,
      },
      {
        source: "/api/:path*",
        destination: `${gateway}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
