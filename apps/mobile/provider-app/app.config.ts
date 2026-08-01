import { ExpoConfig, ConfigContext } from "expo/config";

const VARIANT_NAME_SUFFIX = {
  dev: " Dev",
  development: " Dev",
  preview: " Preview",
  staging: " Staging",
  production: "",
} as const;
const VARIANT_BUNDLE_SUFFIX = {
  dev: ".dev",
  development: ".dev",
  preview: ".preview",
  staging: ".staging",
  production: "",
} as const;

type AppVariant = keyof typeof VARIANT_NAME_SUFFIX;

function resolveVariant(raw: string | undefined): AppVariant {
  const normalized = (raw ?? "dev").toLowerCase();
  if (normalized in VARIANT_NAME_SUFFIX) {
    return normalized as AppVariant;
  }
  return "dev";
}

const VARIANT = resolveVariant(process.env.EXPO_PUBLIC_APP_VARIANT);

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
    // Universal Links: iOS verifies this against
    // https://impilo.mohcc.gov.zw/.well-known/apple-app-site-association.
    // Verification only completes once the real Apple Team ID replaces
    // TEAMID_PENDING in that file (see apps/mobile/DEEP_LINKING.md).
    associatedDomains: ["applinks:impilo.mohcc.gov.zw"],
    infoPlist: {
      NSCameraUsageDescription:
        "Used for telemedicine video consultations, scanning patient IDs, and capturing clinical images.",
      NSMicrophoneUsageDescription:
        "Used for telemedicine audio and video consultations.",
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
      "RECORD_AUDIO",
    ],
    // Android App Links: autoVerify makes the OS check
    // https://impilo.mohcc.gov.zw/.well-known/assetlinks.json against the
    // release-signing certificate. Verified links open the app directly;
    // verification only completes once the real signing SHA256 replaces the
    // placeholder in that file (see apps/mobile/DEEP_LINKING.md). The custom
    // scheme (impilo-provider://) keeps working regardless.
    intentFilters: [
      {
        action: "VIEW",
        autoVerify: true,
        category: ["BROWSABLE", "DEFAULT"],
        data: [
          { scheme: "https", host: "impilo.mohcc.gov.zw", pathPrefix: "/provider" },
          { scheme: "https", host: "impilo.mohcc.gov.zw", pathPrefix: "/work" },
        ],
      },
    ],
  },
  plugins: [
    "expo-web-browser",
    "expo-secure-store",
    "expo-sqlite",
    "@livekit/react-native-expo-plugin",
    [
      "@config-plugins/react-native-webrtc",
      {
        cameraPermission:
          "Used for telemedicine video consultations, scanning patient IDs, and capturing clinical images.",
        microphonePermission:
          "Used for telemedicine audio and video consultations.",
      },
    ],
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
      process.env.EXPO_PUBLIC_API_BASE_URL ??
      (VARIANT === "preview" ? "https://impilo.mohcc.gov.zw" : "http://192.168.100.211:8160"),
    authBaseUrl:
      process.env.EXPO_PUBLIC_AUTH_BASE_URL ??
      (VARIANT === "preview" ? "https://impilo.mohcc.gov.zw" : process.env.EXPO_PUBLIC_KEYCLOAK_URL ?? "http://192.168.100.211:8080"),
    fhirBaseUrl: process.env.EXPO_PUBLIC_FHIR_BASE_URL,
    liveBaseUrl: process.env.EXPO_PUBLIC_LIVE_BASE_URL,
    nompiloBaseUrl: process.env.EXPO_PUBLIC_NOMPILO_BASE_URL,
    webBaseUrl: process.env.EXPO_PUBLIC_WEB_BASE_URL ?? (VARIANT === "preview" ? "https://impilo.mohcc.gov.zw" : undefined),
    redirectUri:
      process.env.EXPO_PUBLIC_REDIRECT_URI ?? "impilo-provider://auth/callback",
    eas: {
      projectId: "impilo-provider",
    },
  },
});
