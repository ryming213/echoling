"""
Youdao translation API sign test.

Run: python youdao_sign_test.py <APP_ID> <APP_KEY> [WORD]

Tries multiple sign variants to isolate the 202 (signature) error.
"""
import hashlib
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


def sign_v1(app_key: str, q: str, salt: str, curtime: str) -> str:
    """truncate rule A: q.len > 20 ? first10 + q.len + last10 : q"""
    if len(q) > 20:
        truncated = q[:10] + str(len(q)) + q[-10:]
    else:
        truncated = q
    src = app_key + truncated + salt + curtime + app_key
    return hashlib.sha256(src.encode("utf-8")).hexdigest()


def sign_v2(app_key: str, q: str, salt: str, curtime: str) -> str:
    """truncate rule B: same as v1 but compare > 20 vs >= 20"""
    if len(q) >= 20:
        truncated = q[:10] + str(len(q)) + q[-10:]
    else:
        truncated = q
    src = app_key + truncated + salt + curtime + app_key
    return hashlib.sha256(src.encode("utf-8")).hexdigest()


def post_form(form: dict) -> str:
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
        print("Usage: python youdao_sign_test.py <APP_ID> <APP_KEY> [WORD]")
        return 1

    app_id = sys.argv[1].strip()
    app_key = sys.argv[2].strip()
    word = sys.argv[3] if len(sys.argv) > 3 else "apple"

    salt = uuid.uuid4().hex
    curtime = str(int(time.time()))

    for label, sign_fn in [("v1 (q>20)", sign_v1), ("v2 (q>=20)", sign_v2)]:
        sign = sign_fn(app_key, word, salt, curtime)
        form = {
            "q": word,
            "from": "en",
            "to": "zh-CHS",
            "appKey": app_id,
            "salt": salt,
            "sign": sign,
            "signType": "v3",
            "curtime": curtime,
        }
        print(f"--- {label} | q='{word}' (len={len(word)}) ---")
        print(f"  sign: {sign}")
        print(f"  response: {post_form(form)}")
        print()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
