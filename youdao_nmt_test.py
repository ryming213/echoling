"""
Youdao NMT API sign test.

⚠️ Correct sign formula (verified):
   sign = sha256(APP_ID + truncate(q) + salt + curtime + APP_SECRET)
   where the two endpoints are DIFFERENT values — the public appId first,
   the private appSecret last. Old code wrongly used appSecret at both
   ends and always got errorCode 202.

truncate(q): q length > 20 ? q[:10] + q.length + q[-10:] : q

Usage: python youdao_nmt_test.py <APP_ID> <APP_SECRET> [WORD]
"""
import hashlib
import sys
import time
import uuid
import urllib.error
import urllib.parse
import urllib.request


def sign(secret_or_key1, secret_or_key2, q, salt, ct):
    trunc = q[:10] + str(len(q)) + q[-10:] if len(q) > 20 else q
    return hashlib.sha256((secret_or_key1 + trunc + salt + ct + secret_or_key2).encode()).hexdigest()


def call(form: dict) -> str:
    body = urllib.parse.urlencode(form).encode("utf-8")
    req = urllib.request.Request(
        "https://openapi.youdao.com/api",
        data=body,
        method="POST",
        headers={"Content-Type": "application/x-www-form-urlencoded"},
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return f"HTTP {e.code}: {e.read().decode('utf-8', errors='replace')}"


def main() -> int:
    if len(sys.argv) < 3:
        print("Usage: python youdao_nmt_test.py <APP_ID> <APP_SECRET> [WORD]")
        return 1
    app_id = sys.argv[1].strip()
    app_secret = sys.argv[2].strip()
    q = sys.argv[3] if len(sys.argv) > 3 else "apple"

    print("=== sign variants (k1 + truncate + salt + curtime + k2) ===")
    variants = [
        ("secret+...+secret (BUG — always 202)", app_secret, app_secret),
        ("appId+...+appSecret (CORRECT)",         app_id,     app_secret),
        ("appSecret+...+appId (reversed)",        app_secret, app_id),
    ]
    for label, k1, k2 in variants:
        salt = uuid.uuid4().hex
        ct = str(int(time.time()))
        s = sign(k1, k2, q, salt, ct)
        form = {
            "q": q, "from": "en", "to": "zh-CHS",
            "appKey": app_id, "salt": salt, "sign": s,
            "signType": "v3", "curtime": ct,
        }
        resp = call(form)
        ec = resp.split('"errorCode":"')[1].split('"')[0] if '"errorCode"' in resp else '?'
        print(f"  {label:42s}  ec={ec:3s}  {resp[:200]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
