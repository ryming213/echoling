"""
Try Youdao new API with different combinations of appKey + Bearer.
Hypothesis: new API still needs appKey but uses Bearer for auth.

Usage: python youdao_param_probe2.py <APP_ID> <APIkey> [WORD]
"""
import sys
import urllib.error
import urllib.parse
import urllib.request

URL = "https://openapi.youdao.com/api"


def call(api_key: str, form: dict) -> str:
    body = urllib.parse.urlencode(form).encode("utf-8")
    req = urllib.request.Request(
        URL, data=body, method="POST",
        headers={
            "Content-Type": "application/x-www-form-urlencoded",
            "Authorization": f"Bearer {api_key}",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return f"HTTP {r.status}: {r.read().decode()[:300]}"
    except urllib.error.HTTPError as e:
        return f"HTTP {e.code}: {e.read().decode('utf-8', errors='replace')[:300]}"


def main() -> int:
    if len(sys.argv) < 3:
        print("Usage: python youdao_param_probe2.py <APP_ID> <APIkey> [WORD]")
        return 1
    app_id = sys.argv[1].strip()
    api_key = sys.argv[2].strip()
    word = sys.argv[3] if len(sys.argv) > 3 else "apple"

    base = {"q": word, "from": "en", "to": "zh-CHS"}
    cases = [
        ("base", base),
        ("+ appKey", {**base, "appKey": app_id}),
        ("+ appKey + signType=v3", {**base, "appKey": app_id, "signType": "v3"}),
        ("+ appKey + signType=v1", {**base, "appKey": app_id, "signType": "v1"}),
        ("+ appKey + doctype=json", {**base, "appKey": app_id, "doctype": "json"}),
        ("+ appKey + ext=mp3", {**base, "appKey": app_id, "ext": "mp3"}),
        ("+ appKey + salt (random) + curtime", {**base, "appKey": app_id, "salt": "abc123", "curtime": "1700000000"}),
        ("+ appKey + doctype=json + ext=mp3", {**base, "appKey": app_id, "doctype": "json", "ext": "mp3"}),
        ("+ appKey + doctype + ext + salt + curtime + signType=v3", {
            **base, "appKey": app_id, "doctype": "json", "ext": "mp3",
            "salt": "abc123", "curtime": "1700000000", "signType": "v3",
        }),
    ]
    for label, form in cases:
        print(f"--- {label} ---")
        print("  ", call(api_key, form))
        print()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
