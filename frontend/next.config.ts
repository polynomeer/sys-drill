import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // docs/COMMERCIALIZATION.md — Dockerfile's runtime stage copies only the
  // standalone output + static assets, not the full node_modules tree.
  output: "standalone",
};

export default nextConfig;
