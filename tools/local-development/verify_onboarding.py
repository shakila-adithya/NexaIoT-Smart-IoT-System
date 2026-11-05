import json
import re
import secrets
import ssl
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path


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


def main():
    repo = Path(__file__).resolve().parents[2]
    base = "https://127.0.0.1:8443"
    broker = "ssl://127.0.0.1:8885"
    ca = repo / "tools" / "local-development" / ".runtime" / "certs" / "ca.crt"
    owner = f"local-onboarding-{uuid.uuid4().hex[:10]}@example.test"
    password = secrets.token_urlsafe(24)
    context = ssl.create_default_context(cafile=str(ca))

    status, _ = call(base, "/api/auth/register", "POST", {
        "fullName": "Local Onboarding Owner", "email": owner, "password": password,
    }, context=context)
    require(status, {201}, "Owner registration")
    status, login = call(base, "/api/auth/login", "POST", {
        "email": owner, "password": password,
    }, context=context)
    require(status, {200}, "Owner login")
    token = login["token"]
    status, device = call(base, "/api/devices", "POST", {
        "name": "Greenhouse Sensor",
        "type": "greenhouse-sensor",
        "location": "Local development greenhouse",
        "templateId": "temperature-humidity-sensor",
    }, token, context)
    require(status, {201}, "Greenhouse Sensor registration")
    device_id = device["id"]
    status, pairing = call(
        base, f"/api/devices/{device_id}/pairing-sessions", "POST", {}, token, context)
    require(status, {201}, "Pairing session")

    python = repo / "simulator" / ".venv" / "bin" / "python.exe"
    if not python.is_file():
        python = repo / "simulator" / ".venv" / "Scripts" / "python.exe"
    script = repo / "simulator" / "onboard_device.py"
    process = subprocess.Popen(
        [str(python), "-u", str(script)],
        cwd=str(repo), stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        stderr=subprocess.PIPE, text=True, bufsize=1,
    )
    inputs = [base, broker, str(ca), owner, password, device["deviceKey"], pairing["pairingToken"]]
    process.stdin.write("\n".join(inputs) + "\n")
    process.stdin.flush()
    output = ""
    activated = False
    stopped = False
    operation_id = None
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        char = process.stdout.read(1)
        if not char:
            if process.poll() is not None:
                break
            continue
        output += char
        if operation_id is None:
            match = re.search(r"Enrollment operation started: ([A-Za-z0-9-]+)\.", output)
            if match:
                operation_id = match.group(1)
        if not activated and "Click 'Start device activation' in NexaIoT" in output:
            if not operation_id:
                raise RuntimeError("The simulator did not report its enrollment operation.")
            status, _ = call(
                base,
                f"/api/devices/{device_id}/enrollment-operations/"
                f"{operation_id}/activation/challenge",
                "POST", {}, token, context)
            require(status, {200, 204}, "Activation challenge")
            activated = True
            process.stdin.write("\n")
            process.stdin.flush()
        if activated and not stopped and "Telemetry is running" in output:
            process.stdin.write("\n")
            process.stdin.flush()
            stopped = True
        if stopped and process.poll() is not None:
            break
    if process.poll() is None:
        process.kill()
        process.wait(timeout=10)
    error_output = process.stderr.read()
    if process.returncode != 0 or not activated or not stopped \
            or "Device activation confirmed." not in output:
        detail = error_output.strip().splitlines()[-1] if error_output.strip() else "no simulator error"
        markers = output[-1200:].replace("\r", "")
        raise RuntimeError(f"Interactive onboarding did not complete: {detail}; output={markers}")
    print("INTERACTIVE_ONBOARDING_VERIFIED")


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"INTERACTIVE_ONBOARDING_FAILED: {error}", file=sys.stderr)
        raise SystemExit(1)
