"""
Last-resort probe: try every conceivable word field name + combos,
also try different language code formats, also try without from/to.

Usage: python youdao_word_probe.py <APIkey> [WORD]
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
            return r.read().decode()[:250]
    except urllib.error.HTTPError as e:
        return e.read().decode("utf-8", errors="replace")[:250]


def main() -> int:
    if len(sys.argv) < 2:
        print("Usage: python youdao_word_probe.py <APIkey> [WORD]")
        return 1
    api_key = sys.argv[1].strip()
    word = sys.argv[2] if len(sys.argv) > 2 else "apple"

    word_fields = ["q", "text", "input", "content", "source", "src", "query", "sentence", "word", "i"]
    from_fields = ["from", "src", "source", "srcLang", "lang_from", "sourceLanguage", "f"]
    to_fields = ["to", "dst", "target", "tgtLang", "lang_to", "targetLanguage", "t"]

    print("=== single word field + from/to (best guess) ===")
    for wf in word_fields:
        form = {wf: word, "from": "en", "to": "zh-CHS"}
        resp = call(api_key, form)
        if '"errorCode":"0"' in resp:
            marker = "✅ SUCCESS"
        elif "errorCode" in resp:
            ec = resp.split('"errorCode":"')[1].split('"')[0]
            marker = f"errorCode={ec}"
        else:
            marker = "?"
        print(f"  {wf:10s} → {marker}: {resp[:120]}")

    print()
    print("=== with Chinese-only to (just 'zh' / 'chs' / '中文') ===")
    for to in ["zh", "zh-CHS", "CHS", "chinese", "中文", "zh-cn", "ZH-CHS"]:
        resp = call(api_key, {"q": word, "from": "en", "to": to})
        marker = "OK" if '"errorCode":"0"' in resp else f"err={resp.split('\"errorCode\":\"')[1].split('\"')[0] if '\"errorCode\"' in resp else '?'}"
        print(f"  to={to:10s} → {marker}: {resp[:120]}")

    print()
    print("=== with English-only from (just 'en' / 'ENG') ===")
    for f in ["en", "ENG", "english", "en-US", "EN"]:
        resp = call(api_key, {"q": word, "from": f, "to": "zh-CHS"})
        marker = "OK" if '"errorCode":"0"' in resp else f"err={resp.split('\"errorCode\":\"')[1].split('\"')[0] if '\"errorCode\"' in resp else '?'}"
        print(f"  from={f:10s} → {marker}: {resp[:120]}")

    print()
    print("=== try get params instead of form ===")
    for url_path in ["/api", "/textTranslate", "/v2/text_translate", "/v2/api", "/translate", "/translation"]:
        url = f"https://openapi.youdao.com{url_path}?q={urllib.parse.quote(word)}&from=en&to=zh-CHS"
        req = urllib.request.Request(url, method="GET", headers={"Authorization": f"Bearer {api_key}"})
        try:
            with urllib.request.urlopen(req, timeout=10) as r:
                body = r.read().decode()[:200]
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", errors="replace")[:200]
        print(f"  GET {url_path:25s} → {body[:150]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
