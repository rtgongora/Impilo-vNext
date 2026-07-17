/**
 * Real, scannable QR encoding the Impilo web-app entry point
 * (https://impilo.mohcc.gov.zw/welcome).
 *
 * The mobile apps are not yet on the stores, so this QR deliberately points to
 * the WEB experience — "scan to open Impilo on your phone (web)", never a fake
 * store deep-link. The module matrix was generated offline (QR version 3, EC
 * level M, byte mode) for that exact URL and renders as self-contained SVG rects
 * (no runtime library, no network request, no external image).
 */

const QR_MATRIX = [
  "11111110101110011111101111111",
  "10000010111100110010001000001",
  "10111010011001110010101011101",
  "10111010111010000100001011101",
  "10111010000001000111001011101",
  "10000010010100100111001000001",
  "11111110101010101010101111111",
  "00000000110101101101000000000",
  "10110111011111101100001001011",
  "01100001100010001111011110001",
  "00010011100100101010011000110",
  "00000101111101100000001010001",
  "11000110011010000100000001100",
  "01010100110101011011001000111",
  "01001110000100100101110000111",
  "11100000101111001011001010010",
  "01011111110000010010100011010",
  "01001001001001010010100101110",
  "10100011001000011100001110100",
  "00111101010100010110101010100",
  "01011110011101001100111111100",
  "00000000110111101110100011111",
  "11111110110111000011101011010",
  "10000010100000000010100011010",
  "10111010010110100100111110110",
  "10111010101010111111010111001",
  "10111010100101100110100100101",
  "10000010000100011000101111010",
  "11111110110110100011110110010",
];

export const QR_WEB_URL = "https://impilo.mohcc.gov.zw/welcome";

export function WebAppQr({ size = 148, className = "" }: { size?: number; className?: string }) {
  const n = QR_MATRIX.length;
  const quiet = 4; // required QR quiet zone (modules)
  const dim = n + quiet * 2;

  const rects: React.ReactNode[] = [];
  for (let r = 0; r < n; r++) {
    for (let c = 0; c < n; c++) {
      if (QR_MATRIX[r][c] === "1") {
        rects.push(<rect key={`${r}-${c}`} x={c + quiet} y={r + quiet} width="1" height="1" />);
      }
    }
  }

  return (
    <svg
      width={size}
      height={size}
      viewBox={`0 0 ${dim} ${dim}`}
      shapeRendering="crispEdges"
      className={className}
      role="img"
      aria-label="QR code to open Impilo on your phone in the web browser"
    >
      <rect x="0" y="0" width={dim} height={dim} fill="#ffffff" />
      <g fill="#0f172a">{rects}</g>
    </svg>
  );
}
