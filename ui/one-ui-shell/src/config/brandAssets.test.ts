import { describe, expect, it } from "vitest";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { resolve } from "node:path";
import { listSovereignServices } from "@/config/serviceBranding";

const PUBLIC_DIR = resolve(__dirname, "../../public");
const SRC_DIR = resolve(__dirname, "..");

/** A path under /public referenced from the app (leading-slash rooted). */
function publicPath(rooted: string): string {
  return resolve(PUBLIC_DIR, rooted.replace(/^\//, ""));
}

function isPng(file: string): boolean {
  const sig = readFileSync(file).subarray(0, 8);
  return sig.equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]));
}

/**
 * Services whose logo asset does not exist yet. These fall back to the Lucide
 * `fallbackIcon` at runtime (ServiceLogo onError), so they do not crash — but
 * the registry path 404s until an asset is supplied. Keep this list SHORT and
 * documented; a new entry here should be a deliberate, reported gap.
 */
const KNOWN_MISSING_LOGOS = new Set(["patient-safety"]);

describe("brand assets — service logos", () => {
  it("every registry logo path resolves to a real PNG (except documented known-missing)", () => {
    for (const entry of listSovereignServices()) {
      expect(entry.logo).toMatch(/^\/brand\/services\/[a-z-]+-logo\.png$/);
      const file = publicPath(entry.logo);
      if (KNOWN_MISSING_LOGOS.has(entry.slug)) {
        expect(existsSync(file), `${entry.slug} unexpectedly present — remove from KNOWN_MISSING_LOGOS`).toBe(false);
        continue;
      }
      expect(existsSync(file), `${entry.slug}: ${entry.logo} missing`).toBe(true);
      expect(isPng(file), `${entry.slug}: not a PNG`).toBe(true);
    }
  });

  it("keeps the un-optimised originals backed up and out of the public serving path", () => {
    const originals = resolve(PUBLIC_DIR, "brand/services/originals");
    expect(existsSync(originals)).toBe(true);
    // every present optimised logo has a backed-up original
    for (const entry of listSovereignServices()) {
      if (KNOWN_MISSING_LOGOS.has(entry.slug)) continue;
      const name = entry.logo.split("/").pop()!;
      expect(existsSync(resolve(originals, name)), `${name} original missing`).toBe(true);
    }
  });
});

describe("brand assets — Impilo platform", () => {
  const PLATFORM_ASSETS = [
    "/brand/logo-rgb.svg",
    "/brand/logo-white.svg",
    "/brand/logo-black.svg",
    "/brand/mark-rgb.svg",
    "/brand/mark-white.svg",
    "/brand/mark-black.svg",
    "/favicon.svg",
  ];

  it("all referenced platform SVG assets exist", () => {
    for (const p of PLATFORM_ASSETS) {
      expect(existsSync(publicPath(p)), `${p} missing`).toBe(true);
    }
  });

  it("layout metadata + manifest reference only existing public assets", () => {
    const layout = readFileSync(resolve(SRC_DIR, "app/layout.tsx"), "utf8");
    const manifest = readFileSync(resolve(SRC_DIR, "app/manifest.ts"), "utf8");
    for (const src of [layout, manifest]) {
      for (const m of src.matchAll(/["'](\/(?:brand\/|favicon)[^"']+\.svg)["']/g)) {
        expect(existsSync(publicPath(m[1])), `${m[1]} referenced but missing`).toBe(true);
      }
    }
    // manifest is linked and theme colour is set
    expect(layout).toContain("/manifest.webmanifest");
    expect(layout).toMatch(/themeColor/);
  });

  it("backs up original platform assets under brand/platform/originals", () => {
    const dir = resolve(PUBLIC_DIR, "brand/platform/originals");
    expect(existsSync(dir)).toBe(true);
    expect(readdirSync(dir).some((f) => f.endsWith(".svg"))).toBe(true);
  });
});

describe("brand assets — no stale/local paths in branding source", () => {
  const FILES = [
    "app/layout.tsx",
    "app/manifest.ts",
    "components/branding/ServiceLogo.tsx",
    "components/branding/ShellAppIcon.tsx",
    "components/brand/ImpiloBrandLogo.tsx",
    "components/PageShell.tsx",
    "config/serviceBranding.ts",
  ];
  it("references no apps/web/public, /tmp, /home/robert, or local filesystem paths", () => {
    for (const rel of FILES) {
      const src = readFileSync(resolve(SRC_DIR, rel), "utf8");
      expect(src).not.toMatch(/apps\/web\/public/);
      expect(src).not.toMatch(/\/home\/robert/);
      expect(src).not.toMatch(/(^|[^a-z])\/tmp\//);
    }
  });
});
