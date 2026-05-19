import { ExpoConfig, ConfigContext } from "expo/config";

const VARIANT = (process.env.EXPO_PUBLIC_APP_VARIANT ?? "dev").toLowerCase();
const VARIANT_NAME_SUFFIX: Record<string, string> = {
  dev: " Dev",
  development: " Dev",
  preview: " Preview",
  staging: " Staging",
  production: "",
};
const VARIANT_BUNDLE_SUFFIX: Record<string, string> = {
  dev: ".dev",
  development: ".dev",
  preview: ".preview",
  staging: ".staging",
  production: "",
};

const APP_NAME =
  process.env.EXPO_PUBLIC_APP_NAME ?? `Impilo Provider${VARIANT_NAME_SUFFIX[VARIANT] ?? ""}`;
const BUNDLE_BASE = "zw.gov.impilo.provider";
const BUNDLE = `${BUNDLE_BASE}${VARIANT_BUNDLE_SUFFIX[VARIANT] ?? ""}`;

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: APP_NAME,
  slug: "impilo-provider",
  version: "0.1.0",
  orientation: "portrait",
  icon: "./assets/icon.png",
  userInterfaceStyle: "light",
  scheme: "impilo-provider",
  splash: {
    image: "./assets/splash.png",
    resizeMode: "contain",
    backgroundColor: "#009739",
  },
  ios: {
    supportsTablet: true,
    bundleIdentifier: BUNDLE,
    buildNumber: "1",
    infoPlist: {
      NSCameraUsageDescription:
        "Used for scanning patient IDs and capturing clinical images.",
      NSLocationWhenInUseUsageDescription:
        "Used to record outreach visit locations.",
      NSFaceIDUsageDescription: "Used for biometric authentication.",
    },
  },
  android: {
    adaptiveIcon: {
      foregroundImage: "./assets/adaptive-icon.png",
      backgroundColor: "#009739",
    },
    package: BUNDLE,
    versionCode: 1,
    permissions: [
      "CAMERA",
      "ACCESS_FINE_LOCATION",
      "ACCESS_COARSE_LOCATION",
      "USE_BIOMETRIC",
      "USE_FINGERPRINT",
      "INTERNET",
      "ACCESS_NETWORK_STATE",
    ],
  },
  plugins: [
    "expo-web-browser",
    "expo-secure-store",
    "expo-sqlite",
    [
      "expo-build-properties",
      {
        android: {
          compileSdkVersion: 35,
          targetSdkVersion: 35,
          minSdkVersion: 24,
          buildToolsVersion: "35.0.0",
        },
        ios: {
          deploymentTarget: "15.1",
        },
      },
    ],
  ],
  extra: {
    appVariant: VARIANT,
    appName: APP_NAME,
    keycloakUrl:
      process.env.EXPO_PUBLIC_KEYCLOAK_URL ?? "http://192.168.100.211:8080",
    keycloakRealm: process.env.EXPO_PUBLIC_KEYCLOAK_REALM ?? "impilo",
    keycloakClientId:
      process.env.EXPO_PUBLIC_KEYCLOAK_CLIENT_ID ?? "provider-app",
    apiBaseUrl:
      process.env.EXPO_PUBLIC_API_BASE_URL ?? "http://192.168.100.211:8160",
    redirectUri:
      process.env.EXPO_PUBLIC_REDIRECT_URI ?? "impilo-provider://auth/callback",
    eas: {
      projectId: "impilo-provider",
    },
  },
});
