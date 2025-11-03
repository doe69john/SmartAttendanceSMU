// /functions/api/[[path]].ts

export const onRequestOptions: PagesFunction = async ({ request }) => {
    // CORS preflight
    const origin = request.headers.get("Origin") || "*";
    return new Response(null, {
      status: 204,
      headers: {
        "Access-Control-Allow-Origin": origin,
        "Access-Control-Allow-Methods": "GET,POST,PUT,PATCH,DELETE,OPTIONS",
        "Access-Control-Allow-Headers": request.headers.get("Access-Control-Request-Headers") || "*",
        "Access-Control-Allow-Credentials": "true",
        "Vary": "Origin",
      },
    });
  };
  
  export const onRequest: PagesFunction = async ({ request, params, env }) => {
    const backend = env.BACKEND_ORIGIN; // set in Pages → Settings → Environment variables
    if (!backend) return new Response("Missing BACKEND_ORIGIN", { status: 500 });
  
    // reconstruct target URL: /api/<path>?<query>
    const url = new URL(request.url);
    const rest = (params.path as string | undefined) ?? "";
    const target = new URL(`/api/${rest}${url.search}`, backend);
  
    // Forward method, headers, body (stream); strip hop-by-hop headers
    const hopByHop = new Set([
      "connection","keep-alive","proxy-authenticate","proxy-authorization",
      "te","trailers","transfer-encoding","upgrade"
    ]);
    const outHeaders = new Headers(request.headers);
    for (const h of hopByHop) outHeaders.delete(h);
  
    const resp = await fetch(target.toString(), {
      method: request.method,
      headers: outHeaders,
      body: ["GET","HEAD"].includes(request.method) ? undefined : request.body,
      redirect: "manual",
    });
  
    // CORS for browser
    const origin = request.headers.get("Origin") || "*";
    const resHeaders = new Headers(resp.headers);
    resHeaders.set("Access-Control-Allow-Origin", origin);
    resHeaders.set("Access-Control-Allow-Credentials", "true");
    resHeaders.append("Vary", "Origin");
  
    return new Response(resp.body, { status: resp.status, headers: resHeaders });
  };
  