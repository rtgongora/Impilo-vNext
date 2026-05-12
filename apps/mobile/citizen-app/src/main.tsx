import { registerRootComponent } from "expo";
import * as Crypto from "expo-crypto";

// 1. ABSOLUTE TOP POLYFILLS - Must run before any other imports
(function polyfill() {
  if (typeof global.TextEncoder === "undefined") {
    // @ts-ignore
    global.TextEncoder = class TextEncoder {
      encode(str: string) {
        const arr = new Uint8Array(str.length);
        for (let i = 0; i < str.length; i++) arr[i] = str.charCodeAt(i);
        return arr;
      }
    };
  }

  if (typeof global.TextDecoder === "undefined") {
    // @ts-ignore
    global.TextDecoder = class TextDecoder {
      decode(arr: Uint8Array) {
        return String.fromCharCode.apply(null, Array.from(arr));
      }
    };
  }

  if (typeof global.btoa === "undefined") {
    // @ts-ignore
    global.btoa = (str: string) => {
      const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
      let output = "";
      for (let i = 0; i < str.length; i += 3) {
        const b1 = str.charCodeAt(i);
        const b2 = i + 1 < str.length ? str.charCodeAt(i + 1) : NaN;
        const b3 = i + 2 < str.length ? str.charCodeAt(i + 2) : NaN;
        const e1 = b1 >> 2;
        const e2 = ((b1 & 3) << 4) | (b2 >> 4);
        const e3 = ((b2 & 15) << 2) | (b3 >> 6);
        const e4 = b3 & 63;
        output += chars.charAt(e1) + chars.charAt(e2) +
                  (isNaN(b2) ? "=" : chars.charAt(e3)) +
                  (isNaN(b3) ? "=" : chars.charAt(e4));
      }
      return output;
    };
  }

  if (!global.crypto) {
    // @ts-ignore
    global.crypto = {};
  }
  
  // @ts-ignore
  global.crypto.getRandomValues = (arr: Uint8Array) => Crypto.getRandomValues(arr);
  
  // @ts-ignore
  global.crypto.subtle = {
    digest: async (algo: any, data: ArrayBuffer) => {
      const name = typeof algo === "string" ? algo : algo.name;
      if (name !== "SHA-256") throw new Error(`Algorithm ${name} not supported`);
      
      const bytes = new Uint8Array(data);

      // 1. Prefer digestBufferAsync (returns ArrayBuffer)
      if (typeof Crypto.digestBufferAsync === 'function') {
        return await Crypto.digestBufferAsync(Crypto.CryptoDigestAlgorithm.SHA256, bytes);
      }
      
      // 2. Fallback to digestAsync/digestStringAsync (returns hex string)
      const digestFn = (Crypto as any).digestAsync || (Crypto as any).digestStringAsync;
      if (typeof digestFn === 'function') {
        // Convert to string for older expo-crypto versions that expect a string
        const binStr = Array.from(bytes).map(b => String.fromCharCode(b)).join('');
        let hex: string;
        try {
          hex = await digestFn(Crypto.CryptoDigestAlgorithm.SHA256, bytes);
        } catch {
          hex = await digestFn(Crypto.CryptoDigestAlgorithm.SHA256, binStr);
        }
        
        const out = new Uint8Array(hex.length / 2);
        for (let i = 0; i < hex.length; i += 2) {
          out[i / 2] = parseInt(hex.substring(i, i + 2), 16);
        }
        return out.buffer;
      }
      
      throw new Error("No supported Expo Crypto digest function found");
    }
  };
  
  // @ts-ignore
  globalThis.crypto = global.crypto;

  if (typeof global.URLSearchParams === "undefined") {
    // @ts-ignore
    global.URLSearchParams = class URLSearchParams {
      private p: Record<string, string> = {};
      constructor(init?: any) {
        if (typeof init === "string") {
          init.split("&").forEach(s => {
            const [k, v] = s.split("=");
            if (k) this.p[decodeURIComponent(k)] = decodeURIComponent(v || "");
          });
        } else if (init) {
          Object.assign(this.p, init);
        }
      }
      set(k: string, v: string) { this.p[k] = v; }
      get(k: string) { return this.p[k] || null; }
      toString() {
        return Object.keys(this.p).map(k => `${encodeURIComponent(k)}=${encodeURIComponent(this.p[k])}`).join("&");
      }
    };
  }
})();

// 2. NOW import the App
import { App } from "./App";

registerRootComponent(App);
