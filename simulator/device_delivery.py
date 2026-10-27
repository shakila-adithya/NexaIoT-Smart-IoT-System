"""HTTPS trust-anchor credential delivery for the reference simulator.

The caller must obtain the short-lived challenge through the owner-authorized
enrollment flow. This module never prints or persists the response unless the
caller explicitly stores it using ``store_credentials``.
"""

import base64
import json
import os
import ssl
import struct
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import urlparse

from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec


PROTOCOL = "NexaIoT/CredentialDelivery/v1"


def _field(value):
    encoded = value.encode("utf-8")
    return struct.pack(">I", len(encoded)) + encoded


def delivery_message(device_key, operation_id, owner_email, mqtt_client_id,
                     fingerprint, challenge, expires_at):
    return b"".join(_field(value) for value in (
        PROTOCOL, device_key, operation_id, owner_email, mqtt_client_id,
        fingerprint, challenge, expires_at,
    ))


def delivery_signature(private_key, device_key, operation_id, owner_email,
                       mqtt_client_id, fingerprint, challenge, expires_at):
    if not isinstance(private_key, ec.EllipticCurvePrivateKey):
        raise ValueError("A device private key is required.")
    return base64.b64encode(private_key.sign(
        delivery_message(device_key, operation_id, owner_email, mqtt_client_id,
                         fingerprint, challenge, expires_at),
        ec.ECDSA(hashes.SHA256()),
    )).decode("ascii")


def request_credentials(base_url, device_id, device_key, operation_id, owner_email,
                        fingerprint, challenge, expires_at, private_key,
                        ca_certificate):
    parsed = urlparse(base_url)
    if parsed.scheme.lower() != "https":
        raise ValueError("Credential delivery requires an HTTPS URL.")
    mqtt_client_id = f"nexaiot-device-{device_key}"
    body = json.dumps({
        "mqttClientId": mqtt_client_id,
        "challenge": challenge,
        "signature": delivery_signature(
            private_key, device_key, operation_id, owner_email, mqtt_client_id,
            fingerprint, challenge, expires_at),
    }, separators=(",", ":")).encode("utf-8")
    context = ssl.create_default_context(cafile=ca_certificate)
    request = urllib.request.Request(
        f"{base_url.rstrip('/')}/api/devices/{device_id}/enrollment-operations/"
        f"{operation_id}/credential-delivery",
        data=body,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    opener = urllib.request.build_opener(_NoRedirect(), urllib.request.HTTPSHandler(
        context=context))
    try:
        with opener.open(request, timeout=10) as response:
            if response.headers.get("Cache-Control", "").lower().find("no-store") < 0:
                raise ValueError("Credential delivery response was not non-cacheable.")
            result = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        raise RuntimeError("Credential delivery was rejected.") from error
    if not isinstance(result, dict) or result.get("mqttClientId") != mqtt_client_id \
            or result.get("username") != mqtt_client_id \
            or not isinstance(result.get("password"), str) or not result["password"]:
        raise RuntimeError("Credential delivery response was invalid.")
    return result


def store_credentials(path, response):
    if not isinstance(response, dict) or not response.get("password"):
        raise ValueError("Credential response is invalid.")
    credential_path = Path(path).expanduser()
    credential_path.parent.mkdir(parents=True, exist_ok=True)
    credential_path.write_text(json.dumps({
        "operationId": response["operationId"],
        "username": response["username"],
        "mqttClientId": response["mqttClientId"],
        "password": response["password"],
    }, separators=(",", ":"), sort_keys=True), encoding="utf-8")
    try:
        os.chmod(credential_path, 0o600)
    except OSError:
        pass


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, fp, code, msg, headers, newurl):
        raise RuntimeError("Credential delivery redirects are not allowed.")
