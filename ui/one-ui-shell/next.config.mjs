import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const registryTemplatesRoot = path.join(__dirname, "..", "..", "registry-templates");

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  output: "standalone",
  distDir: ".next-build",
  transpilePackages: ["shared-ui"],
  experimental: {
    serverComponentsExternalPackages: ["dwv", "konva", "canvas"],
    outputFileTracingRoot: path.join(__dirname, ".."),
    staleTimes: {
      dynamic: 0,
      static: 300,
    },
  },
  eslint: {
    ignoreDuringBuilds: true,
  },
  // Type-check in CI/dev via `npm run type-check`. Skipping inside `next build`
  // avoids Docker BuildKit OOM/EOF crashes on large workspaces during image builds.
  typescript: {
    ignoreDuringBuilds: true,
  },
  webpack: (config, { isServer }) => {
    config.resolve.alias = {
      ...config.resolve.alias,
      "@registry-templates": registryTemplatesRoot,
    };

    if (isServer) {
      config.externals = [...(config.externals || []), "dwv", "konva", "canvas"];
    }

    // DWV ships a pre-minified ESM bundle; Next SWC must not re-parse it (_do duplicate error).
    config.module.rules.unshift({
      test: /[\\/]node_modules[\\/]dwv[\\/]dist[\\/].*\.js$/,
      type: "javascript/auto",
      resolve: {
        fullySpecified: false,
      },
    });

    for (const rule of config.module.rules) {
      if (!rule.oneOf) continue;
      for (const oneOf of rule.oneOf) {
        if (!oneOf.exclude) {
          oneOf.exclude = [];
        } else if (!Array.isArray(oneOf.exclude)) {
          oneOf.exclude = [oneOf.exclude];
        }
        oneOf.exclude.push(/[\\/]node_modules[\\/]dwv[\\/]/);
      }
    }

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
