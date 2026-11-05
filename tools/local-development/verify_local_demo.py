import argparse
import json
import socket
import ssl
import sys
import urllib.error
import urllib.request


def request(url, context, method="GET", headers=None):
    request = urllib.request.Request(url, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(request, context=context, timeout=8) as response:
            return response.status, dict(response.headers), response.read(4096)
    except urllib.error.HTTPError as error:
        return error.code, dict(error.headers), error.read(4096)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ca", required=True)
    parser.add_argument("--backend", required=True)
    parser.add_argument("--frontend", required=True)
    parser.add_argument("--mqtt-host", required=True)
    parser.add_argument("--mqtt-port", required=True, type=int)
    args = parser.parse_args()

    context = ssl.create_default_context(cafile=args.ca)
    health_status, _, health_body = request(args.backend + "/api/health", context)
    if health_status != 200 or b"NexaIoT backend is running" not in health_body:
        raise RuntimeError("backend HTTPS health failed")

    cors_status, cors_headers, _ = request(
        args.backend + "/api/health",
        context,
        method="OPTIONS",
        headers={
            "Origin": args.frontend,
            "Access-Control-Request-Method": "GET",
            "Access-Control-Request-Headers": "authorization,content-type",
        },
    )
    allowed_methods = {
        method.strip().upper()
        for method in cors_headers.get("Access-Control-Allow-Methods", "").split(",")
    }
    allowed_headers = {
        header.strip().lower()
        for header in cors_headers.get("Access-Control-Allow-Headers", "").split(",")
    }
    if (
        cors_status not in (200, 204)
        or cors_headers.get("Access-Control-Allow-Origin") != args.frontend
        or "GET" not in allowed_methods
        or not {"authorization", "content-type"}.issubset(allowed_headers)
    ):
        raise RuntimeError("backend CORS verification failed")

    frontend_status, _, frontend_body = request(args.frontend, ssl._create_unverified_context())
    if frontend_status != 200 or b"<html" not in frontend_body.lower():
        raise RuntimeError("frontend reachability failed")

    with socket.create_connection((args.mqtt_host, args.mqtt_port), timeout=8) as connection:
        with context.wrap_socket(connection, server_hostname=args.mqtt_host):
            pass

    print(json.dumps({
        "backend": "healthy",
        "cors": "healthy",
        "frontend": "reachable",
        "mqtt_tls": "handshake_verified",
    }))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"LOCAL_DEMO_VERIFY_FAILED: {error}", file=sys.stderr)
        raise SystemExit(1)
