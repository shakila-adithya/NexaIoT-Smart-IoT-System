import getpass
import json
import ssl
import sys
import urllib.error
import urllib.request


def call(base, path, method="GET", body=None, token=None, context=None):
    data = None if body is None else json.dumps(body, separators=(",", ":")).encode()
    headers = {"Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(
        base.rstrip("/") + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, context=context, timeout=15) as response:
            raw = response.read().decode()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        return error.code, None


def require(status, expected, label):
    if status not in expected:
        raise RuntimeError(f"{label} failed with HTTP {status}.")


def read_secret(prompt):
    if sys.stdin.isatty():
        return getpass.getpass(prompt)
    print(prompt, end="", flush=True)
    return sys.stdin.readline().rstrip("\r\n")


def main():
    base = sys.argv[1]
    ca = sys.argv[2]
    context = ssl.create_default_context(cafile=ca)
    email = input("Local owner email: ").strip().lower()
    password = read_secret("Local owner password: ")
    if not email or not password:
        raise ValueError("Owner email and password are required.")

    status, _ = call(base, "/api/auth/register", "POST", {
        "fullName": "Local Greenhouse Owner",
        "email": email,
        "password": password,
    }, context=context)
    if status not in {201, 409}:
        raise RuntimeError(f"Owner registration failed with HTTP {status}.")

    status, login = call(base, "/api/auth/login", "POST", {
        "email": email,
        "password": password,
    }, context=context)
    require(status, {200}, "Owner login")
    token = login.get("token") if isinstance(login, dict) else None
    if not token:
        raise RuntimeError("Owner login did not return a session token.")

    status, devices = call(base, "/api/devices", token=token, context=context)
    require(status, {200}, "Device lookup")
    matches = [device for device in devices or []
               if str(device.get("name", "")).casefold() == "greenhouse sensor"]
    if len(matches) > 1:
        raise RuntimeError("More than one local Greenhouse Sensor exists.")
    if matches:
        device = matches[0]
        print(f"LOCAL_DEVICE_READY: {device.get('deviceKey')}", flush=True)
        return 0

    status, device = call(base, "/api/devices", "POST", {
        "name": "Greenhouse Sensor",
        "type": "greenhouse-sensor",
        "location": "Local development greenhouse",
        "templateId": "temperature-humidity-sensor",
    }, token, context)
    require(status, {201}, "Greenhouse Sensor creation")
    print(f"LOCAL_DEVICE_READY: {device.get('deviceKey')}", flush=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        raise SystemExit(130)
    except Exception as error:
        print(f"Local owner setup failed: {error}", file=sys.stderr)
        raise SystemExit(1)
