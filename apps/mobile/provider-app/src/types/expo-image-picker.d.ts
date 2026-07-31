/**
 * Ambient types for expo-image-picker until pnpm install lands the package.
 * Package is declared in provider-app/package.json as expo-image-picker ~16.1.4.
 */
declare module "expo-image-picker" {
  export type ImagePickerAsset = { uri: string };
  export type ImagePickerResult =
    | { canceled: true; assets: null }
    | { canceled: false; assets: ImagePickerAsset[] };

  export function requestCameraPermissionsAsync(): Promise<{ granted: boolean }>;
  export function requestMediaLibraryPermissionsAsync(): Promise<{ granted: boolean }>;
  export function launchCameraAsync(options?: {
    mediaTypes?: string[];
    quality?: number;
  }): Promise<ImagePickerResult>;
  export function launchImageLibraryAsync(options?: {
    mediaTypes?: string[];
    quality?: number;
  }): Promise<ImagePickerResult>;
}
