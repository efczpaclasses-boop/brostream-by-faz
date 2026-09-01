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

CATALOGUE_CHECKS = (
    ("Gay0Day catalogue", "https://gay0day.com/categories/gay/", rb"/videos/[0-9]+/"),
    ("Gay0Day amateur", "https://gay0day.com/categories/amateur/", rb"/videos/[0-9]+/"),
    ("Gay0Day blowjob", "https://gay0day.com/categories/blowjob/", rb"/videos/[0-9]+/"),
)
VIDEO_URL = re.compile(rb'href="(https?://gay0day\.com/videos/[0-9]+/[^"]+/)"', re.IGNORECASE)


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


def check_playback() -> str | None:
    try:
        catalogue = fetch("https://gay0day.com/categories/gay/")
        match = VIDEO_URL.search(catalogue)
        if match is None:
            return "FAIL Gay0Day playback: no video URL found in the catalogue"
        video_url = match.group(1).decode("utf-8")
        page = fetch(video_url)
        if re.search(rb"<source[^>]+(?:mp4|m3u8)", page, flags=re.IGNORECASE):
            print("PASS Gay0Day playback: playable source found")
            return None
        return "FAIL Gay0Day playback: no playable source found"
    except (OSError, RuntimeError, UnicodeDecodeError, urllib.error.URLError) as exc:
        return f"WARN Gay0Day playback: runner could not reach the site ({exc})"


def main() -> int:
    failures = []
    with ThreadPoolExecutor(max_workers=len(CATALOGUE_CHECKS)) as executor:
        results = list(executor.map(lambda check: check_source(*check), CATALOGUE_CHECKS))
    results.append(check_playback())
    for failure in results:
        if failure and failure.startswith("FAIL"):
            failures.append(failure)
        if failure:
            print(failure)
    print(f"Checked {len(results)} source checks; {len(failures)} failed.")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
