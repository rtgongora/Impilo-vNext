import type { NdilaCoordinate } from "./ndila-client";

/** Parse Ndila route polyline (mock format or coordinate pairs) into map coordinates. */
export function routePolylineToCoordinates(
  polyline: string | undefined | null,
  fallback?: { origin: NdilaCoordinate; destination: NdilaCoordinate },
): NdilaCoordinate[] {
  if (!polyline?.trim()) {
    if (fallback) {
      return [fallback.origin, fallback.destination];
    }
    return [];
  }

  const mockMatch = /^mock-poly:(-?\d+\.?\d*),(-?\d+\.?\d*)->(-?\d+\.?\d*),(-?\d+\.?\d*)$/.exec(polyline.trim());
  if (mockMatch) {
    return [
      { latitude: Number(mockMatch[1]), longitude: Number(mockMatch[2]) },
      { latitude: Number(mockMatch[3]), longitude: Number(mockMatch[4]) },
    ];
  }

  const semicolonPairs = polyline.split(";").map((part) => part.trim()).filter(Boolean);
  if (semicolonPairs.length >= 2 && semicolonPairs[0]?.includes(",")) {
    return semicolonPairs
      .map((pair) => {
        const [lat, lng] = pair.split(",").map(Number);
        if (!Number.isFinite(lat) || !Number.isFinite(lng)) return null;
        return { latitude: lat, longitude: lng };
      })
      .filter((point): point is NdilaCoordinate => point != null);
  }

  try {
    return decodeGoogleEncodedPolyline(polyline);
  } catch {
    return fallback ? [fallback.origin, fallback.destination] : [];
  }
}

/** Standard Google encoded polyline decoder (precision 5). */
export function decodeGoogleEncodedPolyline(encoded: string): NdilaCoordinate[] {
  const coordinates: NdilaCoordinate[] = [];
  let index = 0;
  let lat = 0;
  let lng = 0;

  while (index < encoded.length) {
    let shift = 0;
    let result = 0;
    let byte: number;
    do {
      byte = encoded.charCodeAt(index++) - 63;
      result |= (byte & 0x1f) << shift;
      shift += 5;
    } while (byte >= 0x20);
    const deltaLat = (result & 1) !== 0 ? ~(result >> 1) : result >> 1;
    lat += deltaLat;

    shift = 0;
    result = 0;
    do {
      byte = encoded.charCodeAt(index++) - 63;
      result |= (byte & 0x1f) << shift;
      shift += 5;
    } while (byte >= 0x20);
    const deltaLng = (result & 1) !== 0 ? ~(result >> 1) : result >> 1;
    lng += deltaLng;

    coordinates.push({ latitude: lat / 1e5, longitude: lng / 1e5 });
  }

  return coordinates;
}
