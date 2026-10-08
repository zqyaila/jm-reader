#!/usr/bin/env python3
"""Generate desktop/icons/jmreader.ico for the Windows installer (jpackage).

Why a script instead of a checked-in binary blob: the icon is part of the build, and a
reviewable generator means anyone can tweak the mark and re-run it. It has no third-party
dependencies (stdlib zlib + struct only), so it also runs on a bare CI image.

What it draws
-------------
A rounded brand-orange tile with a white open-book mark, in the same visual language as the
app's liquid-glass theme (brand orange #FF6F00, deep page navy behind it).

How the anti-aliasing works
---------------------------
Every shape is rasterised from its *signed distance function*: coverage = clamp(0.5 - d, 0, 1).
That is a one-pixel feather evaluated on a 4x supersampled canvas (1024px), which then gets
box-downsampled into every ICO entry (256/128/64/48/32/16). The 4x buffer is what makes the
16px entry still legible instead of a grey smudge.

Usage
-----
    python make_icon.py            # writes ./jmreader.ico next to this file
"""

from __future__ import annotations

import math
import os
import struct
import zlib

# --- master canvas ---------------------------------------------------------------

SIZE = 256          # logical icon size (px), the largest ICO entry
SS = 4              # supersample factor
S = SIZE * SS       # 1024x1024 working buffer


def _clamp(v: float, lo: float = 0.0, hi: float = 1.0) -> float:
    return lo if v < lo else hi if v > hi else v


def rounded_rect_sdf(x: float, y: float, cx: float, cy: float, hw: float, hh: float, r: float) -> float:
    """Signed distance to a rounded rectangle. Negative inside."""
    dx = abs(x - cx) - (hw - r)
    dy = abs(y - cy) - (hh - r)
    ax, ay = max(dx, 0.0), max(dy, 0.0)
    return math.hypot(ax, ay) + min(max(dx, dy), 0.0) - r


def convex_polygon_sdf(x: float, y: float, pts: list[tuple[float, float]]) -> float:
    """Signed distance to a convex polygon (CCW or CW). Negative inside."""
    # Outward normals, oriented by testing against the centroid.
    n = len(pts)
    ccx = sum(p[0] for p in pts) / n
    ccy = sum(p[1] for p in pts) / n
    best = -1e18
    for i in range(n):
        ax, ay = pts[i]
        bx, by = pts[(i + 1) % n]
        ex, ey = bx - ax, by - ay
        # Edge normal, normalised. For a CW polygon (screen space, y down) this points outward.
        ln = math.hypot(ex, ey) or 1.0
        nx, ny = ey / ln, -ex / ln
        # Make sure it really points outward.
        if (ccx - ax) * nx + (ccy - ay) * ny > 0:
            nx, ny = -nx, -ny
        best = max(best, (x - ax) * nx + (y - ay) * ny)
    return best


def _hex(c: str) -> tuple[int, int, int]:
    c = c.lstrip("#")
    return int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16)


BG_TOP = _hex("FF9A45")
BG_BOTTOM = _hex("E64A00")
FG = _hex("FFFFFF")


def render_master() -> bytearray:
    """RGBA buffer, S x S, straight (non-premultiplied) alpha."""
    buf = bytearray(S * S * 4)

    # --- geometry in logical (256) space, scaled to the working buffer -----------
    cx = cy = SIZE / 2.0

    # Tile: fills the canvas with a small inset so the icon breathes on Windows.
    pad = 10.0

    # Open book: two mirrored convex quadrilaterals meeting at a spine gap.
    #
    # All coordinates below are ABSOLUTE in the 256-space (not offsets from `cy`). The spine
    # edge is deliberately taller than the outer edge on both ends: that is what makes a
    # front-on open book read as two pages fanning away from the viewer instead of as two
    # rectangles. The whole mark is then nudged up so its optical centre matches the tile's.
    gap = 5.5        # half the spine gap
    outer = 78.0     # how far the page edges extend from the centre
    top_outer, bottom_outer = 92.0, 164.0      # outer page edge (recedes)
    top_spine, bottom_spine = 76.0, 180.0      # spine edge (nearer the viewer, so taller)
    nudge = -4.0     # optical centring: the mark's box centre -> the tile's centre

    left_page = [
        (cx - outer, top_outer + nudge),
        (cx - gap, top_spine + nudge),
        (cx - gap, bottom_spine + nudge),
        (cx - outer, bottom_outer + nudge),
    ]
    right_page = [(2 * cx - px, py) for (px, py) in left_page]

    # Pre-scale every constant once.
    def sc(v: float) -> float:
        return v * SS

    hw = hh = sc(SIZE / 2.0 - pad)
    radius = sc(46.0)
    left_scaled = [(sc(px), sc(py)) for px, py in left_page]
    right_scaled = [(sc(px), sc(py)) for px, py in right_page]

    bg_top = BG_TOP
    bg_bottom = BG_BOTTOM

    for py in range(S):
        y = py + 0.5
        # Vertical gradient across the whole tile.
        t = _clamp(y / S)
        br = bg_top[0] + (bg_bottom[0] - bg_top[0]) * t
        bg = bg_top[1] + (bg_bottom[1] - bg_top[1]) * t
        bb = bg_top[2] + (bg_bottom[2] - bg_top[2]) * t

        row = py * S * 4
        for pxi in range(S):
            x = pxi + 0.5
            o = row + pxi * 4

            # 1. Tile background (feathered edge -> AA on the corners).
            cov = _clamp(0.5 - rounded_rect_sdf(x, y, sc(cx), sc(cy), hw, hh, radius))
            if cov <= 0.0:
                continue

            r, g, b = br, bg, bb

            # 2. White book mark composited on top.
            mark = max(
                _clamp(0.5 - convex_polygon_sdf(x, y, left_scaled)),
                _clamp(0.5 - convex_polygon_sdf(x, y, right_scaled)),
            )
            if mark > 0.0:
                r = r + (FG[0] - r) * mark
                g = g + (FG[1] - g) * mark
                b = b + (FG[2] - b) * mark

            buf[o] = int(_clamp(r, 0, 255))
            buf[o + 1] = int(_clamp(g, 0, 255))
            buf[o + 2] = int(_clamp(b, 0, 255))
            buf[o + 3] = int(cov * 255.0 + 0.5)
    return buf


def downsample(src: bytearray, src_size: int, dst_size: int) -> bytearray:
    """Box-average `src` (RGBA) down to `dst_size`. Averages *premultiplied* colour so the
    transparent corners do not bleed dark pixels into the edges."""
    factor = src_size // dst_size
    out = bytearray(dst_size * dst_size * 4)
    area = factor * factor
    for dy in range(dst_size):
        for dx in range(dst_size):
            ar = ag = ab = aa = 0
            for sy in range(dy * factor, (dy + 1) * factor):
                base = (sy * src_size + dx * factor) * 4
                for sx in range(factor):
                    o = base + sx * 4
                    a = src[o + 3]
                    ar += src[o] * a
                    ag += src[o + 1] * a
                    ab += src[o + 2] * a
                    aa += a
            o = (dy * dst_size + dx) * 4
            if aa == 0:
                out[o:o + 4] = b"\x00\x00\x00\x00"
            else:
                out[o] = min(255, int(ar / aa + 0.5))
                out[o + 1] = min(255, int(ag / aa + 0.5))
                out[o + 2] = min(255, int(ab / aa + 0.5))
                out[o + 3] = min(255, int(aa / area + 0.5))
    return out


def encode_png(rgba: bytearray, size: int) -> bytes:
    """Minimal PNG encoder: 8-bit RGBA, filter type 0 on every row."""
    raw = bytearray()
    stride = size * 4
    for y in range(size):
        raw.append(0)  # filter: none
        raw += rgba[y * stride:(y + 1) * stride]

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + chunk(b"IEND", b"")
    )


def build_ico(sizes: list[int]) -> bytes:
    master = render_master()
    entries: list[bytes] = []
    payloads: list[bytes] = []
    offset = 6 + 16 * len(sizes)

    for s in sizes:
        rgba = master if s == S else downsample(master, S, s)
        png = encode_png(rgba, s)
        dim = 0 if s >= 256 else s  # 0 means 256 in the ICO directory
        entries.append(
            struct.pack("<BBBBHHII", dim, dim, 0, 0, 1, 32, len(png), offset)
        )
        payloads.append(png)
        offset += len(png)

    header = struct.pack("<HHH", 0, 1, len(sizes))
    return header + b"".join(entries) + b"".join(payloads)


def main() -> None:
    sizes = [256, 128, 64, 48, 32, 16]
    data = build_ico(sizes)
    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "jmreader.ico")
    with open(out, "wb") as fh:
        fh.write(data)
    print(f"wrote {out} ({len(data)} bytes, entries: {sizes})")


if __name__ == "__main__":
    main()
