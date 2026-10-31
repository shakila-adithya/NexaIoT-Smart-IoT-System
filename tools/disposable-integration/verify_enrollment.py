import argparse
import base64
import json
import os
import struct
import sys
import threading
import time
import uuid
import urllib.error
import urllib.request
import ssl

import paho.mqtt.client as mqtt

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "simulator")))
from device_delivery import delivery_signature
from device_trust import load_or_create_private_key, registration_message, registration_payload
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec


def call(base, path, method="GET", body=None, token=None, context=None):
    data = None if body is None else json.dumps(body, separators=(",", ":")).encode()
    headers = {"Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    request = urllib.request.Request(base.rstrip("/") + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, context=context, timeout=15) as response:
            raw = response.read().decode()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raw = error.read().decode()
        try:
            payload = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            payload = raw
        return error.code, payload


def require(status, expected, payload):
    if status not in expected:
        raise RuntimeError(f"unexpected HTTP status {status}: {payload}")


def activation_signature(key, device_key, operation_id, owner, client_id, fingerprint, challenge, expires):
    fields = ("NexaIoT/Activation/v1", device_key, operation_id, owner, client_id,
              fingerprint, challenge, expires)
    message = b"".join(struct.pack(">I", len(value.encode())) + value.encode() for value in fields)
    return base64.b64encode(key.sign(message, ec.ECDSA(hashes.SHA256()))).decode()


def mqtt_device(credentials, ca, device_key, operation_id, owner, fingerprint, challenge_event):
    received = {}
    connected = threading.Event()
    subscribed = threading.Event()
    client_id = credentials["mqttClientId"]
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=client_id)
    client.username_pw_set(credentials["username"], credentials["password"])
    client.tls_set(ca_certs=ca, cert_reqs=ssl.CERT_REQUIRED)
    client.tls_insecure_set(False)

    def on_connect(instance, userdata, flags, reason_code, properties):
        if reason_code.is_failure:
            return
        connected.set()
        instance.subscribe(f"nexaiot/devices/{device_key}/activation-challenges", qos=1)

    def on_subscribe(instance, userdata, mid, reason_codes, properties):
        if reason_codes and not reason_codes[0].is_failure:
            subscribed.set()

    def on_message(instance, userdata, message):
        try:
            payload = json.loads(message.payload.decode())
            if payload.get("operationId") == operation_id:
                received.update(payload)
                challenge_event.set()
        except (ValueError, UnicodeDecodeError):
            pass

    client.on_connect = on_connect
    client.on_subscribe = on_subscribe
    client.on_message = on_message
    client.connect("127.0.0.1", int(os.environ["NEXAIOT_B46_MQTT_TLS_PORT"]), 15)
    client.loop_start()
    if not connected.wait(15) or not subscribed.wait(15):
        client.loop_stop()
        client.disconnect()
        raise RuntimeError("device MQTT authentication or subscription failed")
    return client, received


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--ca", required=True)
    parser.add_argument("--runtime", required=True)
    args = parser.parse_args()
    context = ssl.create_default_context(cafile=args.ca)
    suffix = uuid.uuid4().hex[:10]
    owner = f"owner-{suffix}@example.test"
    password = "NexaTest-" + uuid.uuid4().hex
    device_key = "NEXA-LIVE-" + suffix.upper()

    status, _ = call(args.base_url, "/api/auth/register", "POST", {
        "fullName": "Disposable Owner", "email": owner, "password": password}, context=context)
    require(status, {201}, "registration")
    status, login = call(args.base_url, "/api/auth/login", "POST", {
        "email": owner, "password": password}, context=context)
    require(status, {200}, login)
    token = login["token"]

    status, device = call(args.base_url, "/api/devices", "POST", {
        "name": "Disposable Device", "type": "smart-fan", "location": "lab"}, token, context)
    require(status, {201}, device)
    device_id = device["id"]
    actual_device_key = device["deviceKey"] or device_key

    status, session = call(args.base_url, f"/api/devices/{device_id}/pairing-sessions", "POST", {}, token, context)
    require(status, {201}, session)
    status, claim = call(args.base_url, f"/api/provisioning/pairing-sessions/{session['sessionId']}/claim", "POST", {
        "pairingToken": session["pairingToken"], "hardwareIdentityHash": "disposable-hardware"}, context=context)
    require(status, {200}, claim)
    if claim.get("status") != "CLAIMED":
        raise RuntimeError(f"pairing was not claimed: {claim}")

    status, operation = call(args.base_url, f"/api/devices/{device_id}/enrollment-operations", "POST", {
        "pairingSessionId": session["sessionId"]}, token, context)
    require(status, {201}, operation)
    operation_id = operation["operationId"]

    key_path = os.path.join(args.runtime, "device-key.pem")
    key = load_or_create_private_key(key_path)
    status, challenge = call(args.base_url, f"/api/devices/{device_id}/enrollment-operations/{operation_id}/trust-anchor/challenge", "POST", {}, token, context)
    require(status, {200}, challenge)
    registration = registration_payload(key, actual_device_key, operation_id, owner, challenge["challenge"])
    invalid = dict(registration)
    invalid["signature"] = base64.b64encode(b"invalid").decode()
    status, _ = call(args.base_url, f"/api/devices/{device_id}/enrollment-operations/{operation_id}/trust-anchor", "POST", invalid, token, context)
    require(status, {400, 401, 403}, "invalid trust-anchor signature")
    status, _ = call(args.base_url, f"/api/devices/{device_id}/enrollment-operations/{operation_id}/trust-anchor", "POST", registration, token, context)
    require(status, {200}, "trust-anchor registration")

    status, delivery_challenge = call(args.base_url, f"/api/devices/{device_id}/enrollment-operations/{operation_id}/credential-delivery/challenge", "POST", {}, token, context)
    require(status, {200}, delivery_challenge)
    fingerprint = delivery_challenge["trustAnchorFingerprint"]
    expires = delivery_challenge["expiresAt"]
    client_id = delivery_challenge["mqttClientId"]
    delivery_owner = delivery_challenge["ownerEmail"]
    delivery = {
        "mqttClientId": client_id,
        "challenge": delivery_challenge["challenge"],
        "signature": delivery_signature(key, actual_device_key, operation_id, delivery_owner, client_id, fingerprint, delivery_challenge["challenge"], expires),
    }
    status, credentials = call(args.base_url, f"/api/devices/{device_id}/enrollment-operations/{operation_id}/credential-delivery", "POST", delivery, context=context)
    require(status, {200}, credentials)
    status, _ = call(args.base_url, f"/api/devices/{device_id}/enrollment-operations/{operation_id}/credential-delivery", "POST", delivery, context=context)
    require(status, {400, 401, 403, 409}, "credential replay")

    status, unauthorized = call(args.base_url, f"/api/devices/{device_id}/enrollment-operations/{operation_id}", context=context)
    require(status, {401}, unauthorized)

    status, second = call(args.base_url, f"/api/devices/{device_id}/pairing-sessions", "POST", {}, token, context)
    require(status, {201}, second)
    time.sleep(3)
    status, expired = call(args.base_url, f"/api/provisioning/pairing-sessions/{second['sessionId']}/claim", "POST", {
        "pairingToken": second["pairingToken"]}, context=context)
    require(status, {200}, expired)
    if expired.get("status") != "INVALID":
        raise RuntimeError(f"expired pairing token was accepted: {expired}")

    challenge_event = threading.Event()
    client, received = mqtt_device(credentials, args.ca, actual_device_key, operation_id, owner, fingerprint, challenge_event)
    other_topic = f"nexaiot/devices/{actual_device_key}-OTHER/commands"
    sub_result = []
    sub_event = threading.Event()
    client.on_subscribe = lambda instance, userdata, mid, reason_codes, properties: (sub_result.extend(reason_codes), sub_event.set())
    client.subscribe(other_topic, qos=1)
    if not sub_event.wait(10) or not sub_result or not sub_result[0].is_failure:
        client.loop_stop(); client.disconnect()
        raise RuntimeError("cross-device MQTT subscription was authorized")

    status, _ = call(args.base_url, f"/api/devices/{device_id}/enrollment-operations/{operation_id}/activation/challenge", "POST", {}, token, context)
    require(status, {200, 204}, "activation challenge")
    if not challenge_event.wait(15):
        client.loop_stop(); client.disconnect()
        raise RuntimeError("device did not receive activation challenge")
    response = {
        "operationId": received["operationId"],
        "mqttClientId": received["mqttClientId"],
        "challenge": received["challenge"],
        "expiresAt": received["expiresAt"],
    }
    response["signature"] = activation_signature(key, actual_device_key, operation_id, owner, client_id, fingerprint, response["challenge"], response["expiresAt"])
    client.publish(f"nexaiot/devices/{actual_device_key}/activation-responses", json.dumps(response, separators=(",", ":")), qos=1).wait_for_publish(10)
    client.loop_stop()
    client.disconnect()

    deadline = time.time() + 15
    while time.time() < deadline:
        status, final = call(args.base_url, f"/api/devices/{device_id}/enrollment-operations/{operation_id}", context=context, token=token)
        if status == 200 and final.get("state") == "COMPLETED" and final.get("deviceVerified"):
            print("LIVE_ENROLLMENT_VERIFIED")
            return 0
        time.sleep(1)
    raise RuntimeError(f"activation did not complete: {final}")


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"LIVE_ENROLLMENT_FAILED: {error}", file=sys.stderr)
        raise SystemExit(1)
