"""
Tencent Cloud TMT translation sign test (TC3-HMAC-SHA256).
Prints every step's intermediate value so we can compare to the App's
logcat output and pinpoint which step is wrong.

Usage: python tencent_sign_test.py <SECRET_ID> <SECRET_KEY> [REGION] [WORD]
"""
import hashlib
import hmac
import json
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone


def sha256_hex(s: str) -> str:
    return hashlib.sha256(s.encode("utf-8")).hexdigest()


def hmac_sha256(key, data: str):
    if isinstance(key, str):
        key = key.encode("utf-8")
    return hmac.new(key, data.encode("utf-8"), hashlib.sha256).digest()


def tencent_tmt_sign(secret_id, secret_key, payload, timestamp, region="ap-guangzhou"):
    """Replicates the TC3-HMAC-SHA256 chain step by step."""
    host = "tmt.tencentcloudapi.com"
    service = "tmt"
    action = "TextTranslate"
    algorithm = "TC3-HMAC-SHA256"

    # Step 0: UTC date
    date = datetime.fromtimestamp(timestamp, tz=timezone.utc).strftime("%Y-%m-%d")
    print(f"  date:        {date}")
    print(f"  timestamp:   {timestamp}")

    # Step 1: canonical request
    http_request_method = "POST"
    canonical_uri = "/"
    canonical_query_string = ""
    canonical_headers = (
        f"content-type:application/json; charset=utf-8\n"
        f"host:{host}\n"
        f"x-tc-action:{action.lower()}\n"
    )
    signed_headers = "content-type;host;x-tc-action"
    hashed_request_payload = sha256_hex(payload)
    canonical_request = (
        f"{http_request_method}\n"
        f"{canonical_uri}\n"
        f"{canonical_query_string}\n"
        f"{canonical_headers}\n"
        f"{signed_headers}\n"
        f"{hashed_request_payload}"
    )
    print(f"  canonical_request:\n{canonical_request}")

    # Step 2: string to sign
    credential_scope = f"{date}/{service}/tc3_request"
    hashed_canonical_request = sha256_hex(canonical_request)
    string_to_sign = (
        f"{algorithm}\n"
        f"{timestamp}\n"
        f"{credential_scope}\n"
        f"{hashed_canonical_request}"
    )
    print(f"  string_to_sign:\n{string_to_sign}")

    # Step 3: signature
    secret_date = hmac_sha256(f"TC3{secret_key}", date)
    secret_service = hmac_sha256(secret_date, service)
    secret_signing = hmac_sha256(secret_service, "tc3_request")
    signature = hmac.new(
        secret_signing, string_to_sign.encode("utf-8"), hashlib.sha256
    ).hexdigest()
    print(f"  signature: {signature}")

    # Step 4: authorization header
    authorization = (
        f"{algorithm} "
        f"Credential={secret_id}/{credential_scope}, "
        f"SignedHeaders={signed_headers}, "
        f"Signature={signature}"
    )
    print(f"  authorization: {authorization}")
    return authorization


def call(authorization, payload, timestamp, region="ap-guangzhou"):
    headers = {
        "Authorization": authorization,
        "Content-Type": "application/json; charset=utf-8",
        "Host": "tmt.tencentcloudapi.com",
        "X-TC-Action": "TextTranslate",
        "X-TC-Timestamp": str(timestamp),
        "X-TC-Version": "2018-03-21",
        "X-TC-Region": region,
    }
    req = urllib.request.Request(
        "https://tmt.tencentcloudapi.com/",
        data=payload.encode("utf-8"),
        method="POST",
        headers=headers,
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return f"HTTP {e.code}: {e.read().decode('utf-8', errors='replace')}"


def main() -> int:
    if len(sys.argv) < 3:
        print("Usage: python tencent_sign_test.py <SECRET_ID> <SECRET_KEY> [REGION] [WORD]")
        return 1
    secret_id = sys.argv[1].strip()
    secret_key = sys.argv[2].strip()
    region = sys.argv[3] if len(sys.argv) > 3 else "ap-guangzhou"
    word = sys.argv[4] if len(sys.argv) > 4 else "apple"

    payload = json.dumps(
        {
            "SourceText": word,
            "Source": "en",
            "Target": "zh",
            "ProjectId": 0,
        },
        separators=(",", ":"),
    )
    timestamp = int(time.time())
    print("=== INPUTS ===")
    print(f"  secret_id:  len={len(secret_id)}")
    print(f"  secret_key: len={len(secret_key)}")
    print(f"  region:     {region}")
    print(f"  payload:    {payload}")
    print()
    print("=== SIGN STEPS ===")
    auth = tencent_tmt_sign(secret_id, secret_key, payload, timestamp, region)
    print()
    print("=== RESPONSE ===")
    print(call(auth, payload, timestamp, region)[:400])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
