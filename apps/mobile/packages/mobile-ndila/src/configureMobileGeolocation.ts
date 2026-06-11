import { configureGeolocation, type GeolocationLike } from "./gpsCapture";

type ExpoLocationModule = {
  requestForegroundPermissionsAsync: () => Promise<{ status: string }>;
  getCurrentPositionAsync: (options?: {
    accuracy?: number;
  }) => Promise<{
    coords: {
      latitude: number;
      longitude: number;
      accuracy?: number | null;
      altitude?: number | null;
      speed?: number | null;
      heading?: number | null;
    };
  }>;
};

function expoGeolocationAdapter(location: ExpoLocationModule): GeolocationLike {
  return {
    getCurrentPosition(onSuccess, onError, options) {
      void location.requestForegroundPermissionsAsync().then((permission) => {
        if (permission.status !== "granted") {
          onError({ code: 1, message: "LOCATION_PERMISSION_DENIED" });
          return;
        }
        location
          .getCurrentPositionAsync({ accuracy: options?.enableHighAccuracy ? 6 : 4 })
          .then((pos) =>
            onSuccess({
              coords: {
                latitude: pos.coords.latitude,
                longitude: pos.coords.longitude,
                accuracy: pos.coords.accuracy ?? undefined,
                altitude: pos.coords.altitude,
                speed: pos.coords.speed,
                heading: pos.coords.heading,
              },
            })
          )
          .catch((err: Error) => onError({ code: 2, message: err.message }));
      });
    },
  };
}

/**
 * Wire Expo Location into the Ndila mobile SDK when the host app provides it.
 */
export function configureMobileGeolocationFromExpo(): boolean {
  try {
    const Location = require("expo-location") as ExpoLocationModule;
    configureGeolocation(expoGeolocationAdapter(Location));
    return true;
  } catch {
    return false;
  }
}
