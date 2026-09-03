#!/usr/bin/env python3
"""Release gate for BroStream catalogue, filtering, duplication and playback."""

from __future__ import annotations

import html
import re
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"
BLOCKED = re.compile(
    r"\b(lesbian|girls?|wom[ae]n|female|milf|mommy?|wife|daughter|sister|girlfriend|"
    r"pussy|vagina|clit|tits|boobs|shemale|trans|tranny|futa|stepmom|schoolgirl)\b",
    re.I,
)

CHECKS = (
    ("ManPorn fresh", "https://manporn.xxx/", rb"/videos/[0-9]+/", 20),
    ("ManPorn Latino", "https://manporn.xxx/categories/latino/", rb"/videos/[0-9]+/", 20),
    ("ManPorn blowjob", "https://manporn.xxx/categories/blowjob/", rb"/videos/[0-9]+/", 20),
    ("GayVids amateur", "https://www.gayvids.tv/categories/amateur/", rb"/videos/[0-9]+/", 10),
    ("GayVids Brazilian", "https://www.gayvids.tv/categories/brazilian/", rb"/videos/[0-9]+/", 10),
    ("GayVids homemade", "https://www.gayvids.tv/categories/homemade/", rb"/videos/[0-9]+/", 10),
    ("GayPornTube PNP", "https://www.gayporntube.com/search/videos/pnp-slam/page1.html", rb"data-video-id=[\"']([0-9]+)", 10),
)


def fetch(url: str, referer: str | None = None, byte_range: bool = False) -> bytes:
    headers = {"User-Agent": UA, "Accept-Encoding": "identity"}
    if referer:
        headers["Referer"] = referer
    if byte_range:
        headers["Range"] = "bytes=0-1023"
    request = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(request, timeout=25) as response:
        if response.status not in (200, 206):
            raise RuntimeError(f"HTTP {response.status}")
        return response.read(3_000_000 if not byte_range else 1024)


def check_catalogue(check: tuple[str, str, bytes, int]) -> tuple[str, bytes]:
    name, url, marker, minimum = check
    page = fetch(url)
    count = len(set(re.findall(marker, page, re.I)))
    if count < minimum:
        raise RuntimeError(f"{name}: only {count} catalogue markers (minimum {minimum})")
    titles = [html.unescape(x.decode("utf-8", "ignore")) for x in re.findall(rb'(?:title|alt)=["\']([^"\']+)', page, re.I)]
    leaks = [title for title in titles if BLOCKED.search(title)]
    print(f"PASS {name}: {count} items; {len(leaks)} blocked-title candidates will be filtered")
    return url, page


def check_playback(base_url: str, page: bytes) -> None:
    match = re.search(rb'href=["\'](https?[^"\']+/videos?/[0-9]+/[^"\']*)', page, re.I)
    if not match:
        raise RuntimeError(f"Playback: no detail URL found on {base_url}")
    detail_url = html.unescape(match.group(1).decode("utf-8", "ignore"))
    detail = fetch(detail_url)
    stream_match = re.search(rb'https?[^"\']+?\.mp4[^"\'< ]*', detail, re.I)
    if not stream_match:
        raise RuntimeError(f"Playback: no MP4 found on {detail_url}")
    stream = html.unescape(stream_match.group(0).decode("utf-8", "ignore").replace("\\/", "/"))
    if stream.endswith(".mp4.jpg"):
        candidates = re.findall(rb'https?[^"\']+?\.mp4[^"\'< ]*', detail, re.I)
        stream = next(
            html.unescape(x.decode("utf-8", "ignore").replace("\\/", "/"))
            for x in candidates if not x.endswith(b".mp4.jpg")
        )
    chunk = fetch(stream, referer=detail_url, byte_range=True)
    if len(chunk) < 512:
        raise RuntimeError(f"Playback: short response from {detail_url}")
    print(f"PASS playback: byte-range stream from {detail_url}")


def main() -> int:
    try:
        with ThreadPoolExecutor(max_workers=len(CHECKS)) as executor:
            results = list(executor.map(check_catalogue, CHECKS))
        for url, page in results[:2]:
            check_playback(url, page)
        print(f"Release gate passed: {len(CHECKS)} catalogues and 2 playback streams.")
        return 0
    except Exception as exc:
        print(f"RELEASE GATE FAILED: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
