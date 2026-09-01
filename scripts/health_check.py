#!/usr/bin/env python3
"""Small, privacy-conscious catalogue health check for BroStream by Faz."""

from __future__ import annotations

import re
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor


USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
)

CHECKS = (
    ("ManPorn catalogue", "https://manporn.xxx/", rb"/videos/[0-9]+/"),
    ("ManPorn blowjob", "https://manporn.xxx/categories/blowjob/", rb"/videos/[0-9]+/"),
    ("GayVids amateur", "https://www.gayvids.tv/categories/amateur/", rb"/videos/[0-9]+/"),
    ("GayVids Brazilian", "https://www.gayvids.tv/categories/brazilian/", rb"/videos/[0-9]+/"),
)


def fetch(url: str) -> bytes:
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Accept": "text/html,application/xhtml+xml",
            "Accept-Encoding": "identity",
        },
    )
    with urllib.request.urlopen(request, timeout=20) as response:
        if response.status != 200:
            raise RuntimeError(f"HTTP {response.status}")
        return response.read(3_000_000)


def check_source(name: str, url: str, marker: bytes) -> str | None:
    error = "unknown failure"
    reached_site = False
    for attempt in range(1, 3):
        try:
            page = fetch(url)
            reached_site = True
            matches = len(re.findall(marker, page, flags=re.IGNORECASE))
            if matches:
                print(f"PASS {name}: catalogue structure found ({matches} markers)")
                return None
            error = "expected catalogue structure was not found"
        except (OSError, RuntimeError, urllib.error.URLError) as exc:
            error = str(exc)
        if attempt < 2:
            time.sleep(attempt * 2)
    if not reached_site:
        return f"WARN {name}: runner could not reach the site ({error})"
    return f"FAIL {name}: {error}"


def main() -> int:
    failures = []
    with ThreadPoolExecutor(max_workers=len(CHECKS)) as executor:
        results = list(executor.map(lambda check: check_source(*check), CHECKS))
    for failure in results:
        if failure and failure.startswith("FAIL"):
            failures.append(failure)
        if failure:
            print(failure)
    print(f"Checked {len(CHECKS)} sources; {len(failures)} failed.")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
