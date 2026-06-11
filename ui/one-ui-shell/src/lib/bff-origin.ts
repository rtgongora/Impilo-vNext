/**

 * Resolve BFF HTTP/WS origins for browser sessions.

 *

 * In the browser we use same-origin paths so Next.js rewrites (HTTP) and the

 * Docker entrypoint WS proxy (production) route through port 3000 only — LAN

 * peers do not need a separate firewall rule for 8160.

 */

export function getBffHttpOrigin(): string {

  if (typeof window === "undefined") {

    return (

      process.env.BFF_URL?.replace(/\/$/, "") ||

      process.env.NEXT_PUBLIC_BFF_URL?.replace(/\/$/, "") ||

      "http://localhost:8160"

    );

  }



  // Relative URLs → /internal/* rewritten by Next to gateway/BFF

  return "";

}



export function getBffWsOrigin(): string {

  if (typeof window === "undefined") {

    const http =

      process.env.BFF_URL?.replace(/\/$/, "") ||

      process.env.NEXT_PUBLIC_BFF_URL?.replace(/\/$/, "") ||

      "http://localhost:8160";

    return http.replace(/^http/i, (scheme: string) =>

      scheme.toLowerCase() === "https" ? "wss" : "ws",

    );

  }



  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";



  // Production Docker: WS proxied on the UI port (see docker-entrypoint.cjs)

  if (process.env.NODE_ENV === "production") {

    return `${protocol}//${window.location.host}`;

  }



  // next dev: no WS proxy — connect to BFF directly

  return `${protocol}//${window.location.hostname}:8160`;

}

