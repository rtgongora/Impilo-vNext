import type { MetadataRoute } from "next";

/**
 * PWA / web app manifest for the Impilo one-ui-shell.
 *
 * Icons currently reference the canonical SVG brand mark (scalable, transparent).
 * Raster PNG icons (192/512 + maskable) and an iOS apple-touch-icon.png are
 * intentionally omitted: no SVG rasteriser (rsvg/inkscape/resvg/cairosvg) or
 * ImageMagick is available on this VM, and upscaling would produce low-quality
 * icons. Generate them from /public/brand/mark-rgb.svg when tooling is available.
 * See docs/runbooks/brand-favicon-assets.md.
 */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Impilo — Health Operating System",
    short_name: "Impilo",
    description: "One Health Operating System — Experience Platform",
    start_url: "/",
    display: "standalone",
    background_color: "#e2e9e4",
    theme_color: "#039748",
    icons: [
      {
        src: "/brand/mark-rgb.svg",
        type: "image/svg+xml",
        sizes: "any",
        purpose: "any",
      },
    ],
  };
}
