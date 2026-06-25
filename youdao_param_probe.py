"""
Probe Youdao new API to find correct parameter names.
errorCode 101 = missing required parameter. We add parameters one at a time
and watch the error message change.

Usage: python youdao_param_probe.py <APIkey> [WORD]
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
    if len(sys.argv) < 2:
        print("Usage: python youdao_param_probe.py <APIkey> [WORD]")
        return 1
    api_key = sys.argv[1].strip()
    word = sys.argv[2] if len(sys.argv) > 2 else "apple"

    # ===== Step 1: empty form — should say "missing X" =====
    print(f"--- empty form ---")
    print("  ", call(api_key, {}))
    print()

    # ===== Step 2: try each candidate field name as the word field =====
    for field in ["q", "text", "input", "content", "source", "src"]:
        print(f"--- with {field}='{word}' only ---")
        print("  ", call(api_key, {field: word}))
        print()

    # ===== Step 3: try combinations =====
    for fields in [
        {"q": word, "from": "en", "to": "zh-CHS"},
        {"text": word, "from": "en", "to": "zh-CHS"},
        {"text": word, "source": "en", "target": "zh"},
        {"text": word, "lang_from": "en", "lang_to": "zh"},
        {"text": word, "srcLang": "en", "tgtLang": "zh"},
        {"text": word, "type": "text"},
    ]:
        print(f"--- {fields} ---")
        print("  ", call(api_key, fields))
        print()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
