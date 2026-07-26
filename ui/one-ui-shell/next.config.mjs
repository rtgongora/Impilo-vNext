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
    /**
     * Next evaluates rewrites at BUILD time and bakes the result into
     * routes-manifest.json; `next start` never re-reads these vars. So an image
     * built without them has the localhost fallback compiled in permanently, and
     * no amount of correct runtime env can fix it.
     *
     * That is exactly how /api/* shipped pointing at localhost:10000 — a host
     * that cannot exist in a single-container pod — and every /api/v1/* feature
     * (facility claim, facility lifecycle, site self-service, authorize,
     * speech-to-text) returned 500 via ECONNREFUSED while the deployment looked
     * healthy and its runtime env looked correct.
     *
     * A localhost default is only ever right for local dev. In a production
     * build, fail loudly and name the variable instead of baking in a target
     * that is guaranteed to be unreachable.
     */
    const requireUpstream = (value, envNames, devFallback) => {
      if (value) return value;
      if (process.env.NODE_ENV === "production") {
        throw new Error(
          `[next.config] Missing ${envNames.join(" / ")} at BUILD time. ` +
            `Rewrites are baked into the build, so this would ship "${devFallback}" ` +
            `into the image and every proxied route would fail with ECONNREFUSED at runtime. ` +
            `Export it before building (e.g. --build-arg / docker build env).`,
        );
      }
      return devFallback;
    };

    const gateway = requireUpstream(
      process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_API_GATEWAY_URL,
      ["API_GATEWAY_URL", "NEXT_PUBLIC_API_GATEWAY_URL"],
      "http://localhost:10000",
    );
    const bff = requireUpstream(
      process.env.BFF_URL || process.env.NEXT_PUBLIC_BFF_URL,
      ["BFF_URL", "NEXT_PUBLIC_BFF_URL"],
      "http://localhost:8160",
    );
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
  async redirects() {
    return [
      { source: "/services", destination: "/#services", permanent: true },
      { source: "/solutions", destination: "/#services", permanent: true },
      { source: "/features", destination: "/#services", permanent: true },
      { source: "/resources", destination: "/welcome/health-info", permanent: true },
      { source: "/docs", destination: "/welcome/health-info", permanent: true },
      { source: "/training", destination: "/welcome/learning", permanent: true },
      { source: "/apps", destination: "/download", permanent: true },
      { source: "/community", destination: "/get-involved", permanent: true },
      { source: "/technical", destination: "/about#how-impilo-works", permanent: true },
    ];
  },
};

export default nextConfig;
