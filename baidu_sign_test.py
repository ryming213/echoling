"""
Test Baidu Translate API with the same MD5 sign algorithm as the Android app.
Run: python baidu_sign_test.py <APP_ID> <APP_KEY> [WORD]
"""
import hashlib
import sys
import time
import urllib.error
import urllib.parse
import urllib.request


def preview(s: str) -> str:
    if len(s) < 4:
        return s
    return f"{s[:2]}...{s[-2:]}"


def main() -> int:
    if len(sys.argv) < 3:
        print("Usage: python baidu_sign_test.py <APP_ID> <APP_KEY> [WORD]")
        print("Example: python baidu_sign_test.py 20240101001678901 mysecretkey32charsxxxxxxxxx apple")
        return 1

    app_id = sys.argv[1].strip()
    app_key = sys.argv[2].strip()
    word = sys.argv[3] if len(sys.argv) > 3 else "apple"

    salt = str(int(time.time() * 1000))
    sign_src = app_id + word + salt + app_key
    sign = hashlib.md5(sign_src.encode("utf-8")).hexdigest()

    expected_len = len(app_id) + len(word) + len(salt) + len(app_key)
    len_ok = "OK" if len(sign_src) == expected_len else "MISMATCH"

    print("=== INPUT ===")
    print(f"APP_ID   : len={len(app_id)}  preview={preview(app_id)!r}")
    print(f"APP_KEY  : len={len(app_key)} preview={preview(app_key)!r}")
    print(f"WORD     : {word!r} (len={len(word)})")
    print(f"SALT     : {salt!r} (len={len(salt)})")
    print(f"sign_src : len={len(sign_src)}  expected={expected_len} -> {len_ok}")
    print(f"sign     : {sign}")

    q = urllib.parse.quote(word)
    url = (
        f"https://fanyi-api.baidu.com/api/trans/vip/translate"
        f"?q={q}&from=en&to=zh&appid={app_id}&salt={salt}&sign={sign}"
    )
    print()
    print("=== REQUEST URL ===")
    print(url)
    print()

    try:
        with urllib.request.urlopen(url, timeout=10) as r:
            body = r.read().decode("utf-8")
        print("=== RESPONSE ===")
        print(body)
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        print(f"=== HTTP ERROR {e.code} ===")
        print(body)
    except Exception as e:
        print(f"=== ERROR: {type(e).__name__}: {e} ===")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
