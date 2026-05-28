import { registerRootComponent } from "expo";
import * as Crypto from "expo-crypto";
import { registerGlobals } from "@livekit/react-native";
import { App } from "./App";

// Robust polyfills for mobile (React Native / Expo)
if (!global.crypto) {
  const cryptoPolyfill = {
    getRandomValues: (byteArray: Uint8Array) => Crypto.getRandomValues(byteArray),
    subtle: {
      digest: (algorithm: string | { name: string }, data: ArrayBuffer) => {
        const algo = typeof algorithm === "string" ? algorithm : algorithm.name;
        if (algo === "SHA-256") {
          return Crypto.digest(Crypto.CryptoDigestAlgorithm.SHA256, data);
        }
        throw new Error(`Algorithm ${algo} not supported by polyfill`);
      },
    },
  };
  // @ts-ignore
  global.crypto = cryptoPolyfill;
  // @ts-ignore
  globalThis.crypto = cryptoPolyfill;
}

if (typeof TextEncoder === "undefined") {
  // @ts-ignore
  global.TextEncoder = class TextEncoder {
    encode(str: string) {
      const arr = new Uint8Array(str.length);
      for (let i = 0; i < str.length; i++) {
        arr[i] = str.charCodeAt(i);
      }
      return arr;
    }
  };
}

if (typeof TextDecoder === "undefined") {
  // @ts-ignore
  global.TextDecoder = class TextDecoder {
    decode(arr: Uint8Array) {
      return String.fromCharCode.apply(null, Array.from(arr));
    }
  };
}

if (typeof btoa === "undefined") {
  const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
  // @ts-ignore
  global.btoa = (input: string) => {
    let str = input;
    let output = "";
    for (
      let block = 0, charCode, i = 0, map = chars;
      str.charAt(i | 0) || (map = "=", i % 1);
      output += map.charAt(63 & (block >> (8 - (i % 1) * 8)))
    ) {
      charCode = str.charCodeAt((i += 3 / 4));
      if (charCode > 0xff) {
        throw new Error("'btoa' failed: The string to be encoded contains characters outside of the Latin1 range.");
      }
      block = (block << 8) | charCode;
    }
    return output;
  };
}

registerGlobals();
registerRootComponent(App);
