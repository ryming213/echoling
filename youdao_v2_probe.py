"""
Youdao /v2/api endpoint probe — found via GET returning a different error format.
Hypothesis: this is the new API, probably takes JSON body or different params.

Usage: python youdao_v2_probe.py <APIkey> [WORD]
"""
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

URL = "https://openapi.youdao.com/v2/api"


def call(api_key: str, body=None, form=None, headers_extra=None):
    headers = {"Authorization": f"Bearer {api_key}"}
    if headers_extra:
        headers.update(headers_extra)
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers.setdefault("Content-Type", "application/json")
    elif form is not None:
        data = urllib.parse.urlencode(form).encode("utf-8")
        headers.setdefault("Content-Type", "application/x-www-form-urlencoded")
    else:
        data = b""
    req = urllib.request.Request(URL, data=data, method="POST", headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return f"HTTP {r.status}: {r.read().decode()[:400]}"
    except urllib.error.HTTPError as e:
        return f"HTTP {e.code}: {e.read().decode('utf-8', errors='replace')[:400]}"


def main() -> int:
    if len(sys.argv) < 2:
        print("Usage: python youdao_v2_probe.py <APIkey> [WORD]")
        return 1
    api_key = sys.argv[1].strip()
    word = sys.argv[2] if len(sys.argv) > 2 else "apple"

    print("=== JSON body, no extra headers ===")
    print(" empty  →", call(api_key, body={}))
    print(" q=apple,from=en,to=zh →", call(api_key, body={"q": word, "from": "en", "to": "zh-CHS"}))
    print(" text=apple,from=en,to=zh →", call(api_key, body={"text": word, "from": "en", "to": "zh-CHS"}))
    print(" input=apple,from=en,to=zh →", call(api_key, body={"input": word, "from": "en", "to": "zh-CHS"}))
    print(" q=apple,src=en,tgt=zh →", call(api_key, body={"q": word, "src": "en", "tgt": "zh-CHS"}))

    print()
    print("=== form body on /v2/api ===")
    print(" q=apple,from=en,to=zh →", call(api_key, form={"q": word, "from": "en", "to": "zh-CHS"}))

    print()
    print("=== try with X-Youdao-* headers ===")
    print(call(api_key, body={"q": word, "from": "en", "to": "zh-CHS"},
               headers_extra={"X-Youdao-Auth-Mode": "apikey"}))

    print()
    print("=== try with subPath/body ===")
    print(" with body.body →", call(api_key, body={"body": {"q": word}, "header": {"from": "en", "to": "zh-CHS"}}))

    print()
    print("=== send as query string on /v2/api ===")
    qs = urllib.parse.urlencode({"q": word, "from": "en", "to": "zh-CHS"})
    req = urllib.request.Request(f"{URL}?{qs}", method="GET",
                                  headers={"Authorization": f"Bearer {api_key}"})
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            print(" GET ?q=... →", r.read().decode()[:300])
    except urllib.error.HTTPError as e:
        print(" GET ?q=... →", e.read().decode("utf-8", errors="replace")[:300])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
