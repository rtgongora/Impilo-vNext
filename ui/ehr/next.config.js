/** @type {import('next').NextConfig} */
const nextConfig = {
  transpilePackages: ["shared-ui"],
  output: "standalone",
};

module.exports = nextConfig;
