const path = require("path");

const registryTemplatesRoot = path.join(__dirname, "..", "..", "registry-templates");

/** @type {import('next').NextConfig} */
const nextConfig = {
  transpilePackages: ["shared-ui"],
  output: "standalone",
  experimental: {
    externalDir: true,
  },
  webpack: (config) => {
    config.resolve.alias = {
      ...config.resolve.alias,
      "@registry-templates": registryTemplatesRoot,
    };
    return config;
  },
};

module.exports = nextConfig;
