"""
Comprehensive Youdao auth test.
Tries:
  1) v3 sign with both AppSecret candidates
  2) Bearer token with APIkey (new auth scheme)
  3) AppID + key in url param (newer endpoint)
  4) Empty appSecret to see what error 202 actually complains about

Usage: python youdao_auth_test.py <APP_ID> <CANDIDATE_SECRET_32> <CANDIDATE_APIKEY_16> [WORD]
"""
import hashlib
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


def post_form(url, form: dict, extra_headers: dict | None = None) -> tuple[int, str]:
    body = urllib.parse.urlencode(form).encode("utf-8")
    headers = {"Content-Type": "application/x-www-form-urlencoded"}
    if extra_headers:
        headers.update(extra_headers)
    req = urllib.request.Request(url, data=body, method="POST", headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, r.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")


def sign_v3(secret, q, salt, curtime):
    truncated = q[:10] + str(len(q)) + q[-10:] if len(q) > 20 else q
    return hashlib.sha256((secret + truncated + salt + curtime + secret).encode()).hexdigest()


def case(label, url, form=None, headers=None):
    print(f"--- {label} ---")
    code, body = post_form(url, form or {}, headers or {})
    print(f"   HTTP {code}: {body[:300]}")
    print()
    return code, body


def main() -> int:
    if len(sys.argv) < 4:
        print("Usage: python youdao_auth_test.py <APP_ID> <CANDIDATE_SECRET> <CANDIDATE_APIKEY> [WORD]")
        return 1
    app_id = sys.argv[1].strip()
    candidate_secret = sys.argv[2].strip()  # 32-char alphanumeric — likely the appSecret
    candidate_apikey = sys.argv[3].strip()  # 16-char hex — likely the APIkey
    word = sys.argv[4] if len(sys.argv) > 4 else "apple"

    # ============== LEGACY V3 SIGN (openapi.youdao.com/api) ==============
    print("==== LEGACY V3 SIGN ====")
    for label, secret in [
        ("v3 sign with 32-char", candidate_secret),
        ("v3 sign with 16-char (APIkey)", candidate_apikey),
        ("v3 sign with empty secret", ""),
    ]:
        salt = uuid.uuid4().hex
        curtime = str(int(time.time()))
        sign = sign_v3(secret, word, salt, curtime)
        form = {
            "q": word, "from": "en", "to": "zh-CHS",
            "appKey": app_id, "salt": salt, "sign": sign,
            "signType": "v3", "curtime": curtime,
        }
        case(label, "https://openapi.youdao.com/api", form=form)

    # ============== NEW APIKEY-BEARER (NEW endpoint?) ==============
    print("==== NEW BEARER APIKEY ====")
    # Try the 2 known Youdao text-translate endpoints with Bearer
    for label, url in [
        ("Bearer APIkey → /api", "https://openapi.youdao.com/api"),
        ("Bearer APIkey → /v2/translation", "https://openapi.youdao.com/v2/translation"),
        ("Bearer APIkey → /v1/translate", "https://openapi.youdao.com/v1/translate"),
        ("Bearer APIkey → ai-demo endpoint", "https://aidemo.youdao.com/translate"),
    ]:
        for header_name in ["Authorization", "Api-Key"]:
            h = {header_name: f"Bearer {candidate_apikey}"}
            form = {"q": word, "from": "en", "to": "zh-CHS"}
            case(f"{label} ({header_name})", url, form=form, headers=h)
            h = {header_name: candidate_apikey}  # without "Bearer "
            case(f"{label} ({header_name}, no Bearer)", url, form=form, headers=h)

    # ============== SIGN WITH NO CURTIME / SALT ==============
    print("==== SIGN WITHOUT CURTIME ====")
    for label, secret in [("no-curtime 32-char", candidate_secret), ("no-curtime 16-char", candidate_apikey)]:
        salt = uuid.uuid4().hex
        sign = sign_v3(secret, word, salt, "")  # no curtime
        form = {
            "q": word, "from": "en", "to": "zh-CHS",
            "appKey": app_id, "salt": salt, "sign": sign,
            "signType": "v3",
        }
        case(label, "https://openapi.youdao.com/api", form=form)

    # ============== APIKEY AS QUERY PARAM ==============
    print("==== APIKEY AS QUERY PARAM ====")
    qs = urllib.parse.urlencode({"APIkey": candidate_apikey, "q": word, "from": "en", "to": "zh-CHS"})
    for label, base in [
        ("APIkey in query", "https://openapi.youdao.com/api"),
        ("APIkey in query v2", "https://openapi.youdao.com/v2/translation"),
    ]:
        try:
            req = urllib.request.Request(f"{base}?{qs}", method="POST",
                                          headers={"Content-Type": "application/x-www-form-urlencoded"})
            with urllib.request.urlopen(req, timeout=10) as r:
                print(f"--- {label} ---  HTTP {r.status}: {r.read().decode()[:300]}")
        except urllib.error.HTTPError as e:
            print(f"--- {label} ---  HTTP {e.code}: {e.read().decode('utf-8', errors='replace')[:300]}")
        print()

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
