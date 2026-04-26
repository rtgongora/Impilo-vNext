import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const registryTemplatesRoot = path.join(__dirname, '..', '..', 'registry-templates');

/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  distDir: '.next-build',
  experimental: {
    externalDir: true,
  },
  webpack: (config) => {
    config.resolve.alias = {
      ...config.resolve.alias,
      '@registry-templates': registryTemplatesRoot,
    };
    return config;
  },
  async rewrites() {
    const gateway = process.env.NEXT_PUBLIC_API_GATEWAY_URL || 'http://localhost:10000';
    const bff = process.env.NEXT_PUBLIC_BFF_URL || 'http://localhost:8160';
    return [
      {
        source: '/internal/:path*',
        destination: `${bff}/internal/:path*`,
      },
      {
        source: '/api/:path*',
        destination: `${gateway}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
