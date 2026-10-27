"""P-256 trust-anchor helpers for the reference simulator.

The private key is generated and retained locally. It is never sent to NexaIoT;
only the public-key registration and challenge signature are sent by a caller.
This is software key protection, not hardware attestation.
"""

import base64
import hashlib
import os
import struct
from pathlib import Path

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec


PROTOCOL = "NexaIoT/DeviceTrustAnchor/v1"
ACTIVATION_PROTOCOL = "NexaIoT/Activation/v1"


def _field(value):
    encoded = value.encode("utf-8")
    return struct.pack(">I", len(encoded)) + encoded


def registration_message(device_key, operation_id, owner_email, mqtt_client_id, challenge, fingerprint):
    return b"".join(_field(value) for value in (
        PROTOCOL, device_key, operation_id, owner_email, mqtt_client_id, challenge, fingerprint
    ))


def load_or_create_private_key(path):
    key_path = Path(path).expanduser()
    key_path.parent.mkdir(parents=True, exist_ok=True)
    if key_path.exists():
        key = serialization.load_pem_private_key(key_path.read_bytes(), password=None)
        if not isinstance(key, ec.EllipticCurvePrivateKey) or key.curve.name != "secp256r1":
            raise ValueError("Stored simulator key is not a P-256 private key.")
        return key

    key = ec.generate_private_key(ec.SECP256R1())
    key_path.write_bytes(key.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption(),
    ))
    try:
        os.chmod(key_path, 0o600)
    except OSError:
        pass
    return key


def registration_payload(private_key, device_key, operation_id, owner_email, challenge):
    if not isinstance(private_key, ec.EllipticCurvePrivateKey) or private_key.curve.name != "secp256r1":
        raise ValueError("A P-256 private key is required.")
    public_der = private_key.public_key().public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    public_key = base64.b64encode(public_der).decode("ascii")
    fingerprint = hashlib.sha256(public_der).hexdigest()
    client_id = f"nexaiot-device-{device_key}"
    signature = private_key.sign(
        registration_message(device_key, operation_id, owner_email, client_id, challenge, fingerprint),
        ec.ECDSA(hashes.SHA256()),
    )
    return {
        "mqttClientId": client_id,
        "publicKey": public_key,
        "signature": base64.b64encode(signature).decode("ascii"),
    }


def activation_message(device_key, operation_id, owner_email, mqtt_client_id,
                       fingerprint, challenge, expires_at):
    return b"".join(_field(value) for value in (
        ACTIVATION_PROTOCOL, device_key, operation_id, owner_email, mqtt_client_id,
        fingerprint, challenge, expires_at,
    ))


def activation_signature(private_key, device_key, operation_id, owner_email,
                         mqtt_client_id, fingerprint, challenge, expires_at):
    if not isinstance(private_key, ec.EllipticCurvePrivateKey):
        raise ValueError("A device private key is required.")
    return base64.b64encode(private_key.sign(
        activation_message(device_key, operation_id, owner_email, mqtt_client_id,
                           fingerprint, challenge, expires_at),
        ec.ECDSA(hashes.SHA256()),
    )).decode("ascii")
