import argparse
import json
import subprocess
import ssl
import urllib.error
import urllib.request
import uuid


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
        return error.code, error.read().decode()


def require(status, expected, label):
    if status not in expected:
        raise RuntimeError(f"{label}: unexpected HTTP status {status}")


def register(base, email, password, context):
    status, _ = call(base, "/api/auth/register", "POST", {
        "fullName": "Disposable Authorization Test",
        "email": email,
        "password": password,
    }, context=context)
    require(status, {201}, "registration")


def login(base, email, password, context):
    status, payload = call(base, "/api/auth/login", "POST", {
        "email": email,
        "password": password,
    }, context=context)
    require(status, {200}, "login")
    return payload["token"]


def set_role(mongo_port, email, role=None, remove=False):
    if remove:
        operation = f"db.users.deleteOne({{email:{json.dumps(email)}}})"
    else:
        operation = f"db.users.updateOne({{email:{json.dumps(email)}}}, {{$set:{{role:{json.dumps(role)}}}}})"
    result = subprocess.run([
        "mongosh", "--quiet", f"mongodb://127.0.0.1:{mongo_port}/nexaiot_b46", "--eval", operation
    ], capture_output=True, text=True, timeout=15)
    if result.returncode != 0:
        raise RuntimeError("isolated MongoDB role update failed")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--ca", required=True)
    parser.add_argument("--mongo-port", required=True, type=int)
    args = parser.parse_args()
    context = ssl.create_default_context(cafile=args.ca)
    suffix = uuid.uuid4().hex[:12]
    ordinary_email = f"ordinary-{suffix}@example.test"
    admin_email = f"admin-{suffix}@example.test"
    ordinary_password = "NexaTest-" + uuid.uuid4().hex
    admin_password = "NexaTest-" + uuid.uuid4().hex

    status, _ = call(args.base_url, "/api/admin/stats", context=context)
    require(status, {401}, "anonymous admin access")

    register(args.base_url, ordinary_email, ordinary_password, context)
    register(args.base_url, admin_email, admin_password, context)

    ordinary_token = login(args.base_url, ordinary_email, ordinary_password, context)
    status, _ = call(args.base_url, "/api/admin/stats", token=ordinary_token, context=context)
    require(status, {403}, "persisted non-admin access")

    admin_token = login(args.base_url, admin_email, admin_password, context)
    status, _ = call(args.base_url, "/api/admin/stats", token=admin_token, context=context)
    require(status, {403}, "persisted USER role before promotion")

    set_role(args.mongo_port, admin_email, role="ADMIN")
    status, _ = call(args.base_url, "/api/admin/stats", token=admin_token, context=context)
    require(status, {200}, "persisted ADMIN role")

    set_role(args.mongo_port, admin_email, role="INVALID")
    status, _ = call(args.base_url, "/api/admin/stats", token=admin_token, context=context)
    require(status, {403}, "invalid persisted role")

    set_role(args.mongo_port, admin_email, remove=True)
    status, _ = call(args.base_url, "/api/admin/stats", token=admin_token, context=context)
    require(status, {401}, "missing persisted user")

    print("MONGODB_ADMIN_AUTHORIZATION_VERIFIED")


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"MONGODB_ADMIN_AUTHORIZATION_FAILED: {error}")
        raise SystemExit(1)
