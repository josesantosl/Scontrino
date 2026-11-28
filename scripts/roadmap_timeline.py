#!/usr/bin/env python3
"""Draw the second-level headings of RoadMap.org as a vertical SVG timeline.

Order, top to bottom:
  1. DONE and KILL with a CLOSED date, oldest first ("Heading: 2026-11-09").
     KILL headings are struck through.
  2. STRT, marked with a white ball on the line.
  3. WAIT and HOLD.
  4. TODO.
  5. IDEA.
Open headings with a DEADLINE show it after a colon, in red when it is less
than a week away or already past (as of the day the script runs). Keywords
are not shown.

Usage: scripts/roadmap_timeline.py [RoadMap.org] [-o RoadMap.svg]
Standard library only.
"""

import argparse
import re
import sys
from datetime import date
from pathlib import Path
from xml.sax.saxutils import escape

KEYWORDS = ("TODO", "STRT", "WAIT", "HOLD", "IDEA", "DONE", "KILL")

HEADING_RE = re.compile(r"^(\*+)\s+(.*?)\s*$")
CLOSED_RE = re.compile(r"CLOSED:\s*\[(\d{4}-\d{2}-\d{2})")
DEADLINE_RE = re.compile(r"DEADLINE:\s*<(\d{4}-\d{2}-\d{2})")
TAGS_RE = re.compile(r"\s+:[\w@#%:]+:$")
COOKIE_RE = re.compile(r"\s*\[(?:#\w|\d*/\d*|\d*%)\]")

# Group order on the timeline and how each group is drawn.
GROUPS = ["closed", "STRT", "waiting", "TODO", "IDEA"]
GROUP_OF = {"STRT": "STRT", "WAIT": "waiting", "HOLD": "waiting",
            "TODO": "TODO", "IDEA": "IDEA"}
DOT_COLOR = {"DONE": "#2e7d32", "KILL": "#9e9e9e", "STRT": "#ffffff",
             "WAIT": "#ef6c00", "HOLD": "#ef6c00", "TODO": "#1565c0",
             "IDEA": "#8e24aa"}
TEXT_COLOR = {"KILL": "#757575", "IDEA": "#555555"}
DATE_COLOR = "#9e9e9e"
DUE_SOON_COLOR = "#c0605a"  # muted red for close or missed deadlines
DUE_SOON_DAYS = 7

# Layout, in px.
FONT_SIZE = 13
CHAR_WIDTH = 7.4  # rough average for a 13 px sans-serif, used for the width
LINE_X = 30
TEXT_X = LINE_X + 20
TOP = 30
ROW = 24
GROUP_GAP = 10
BOTTOM = 40
DOT_R = 5
BALL_R = 8


def parse(path):
    """Return the level-2 headings that carry a keyword, with their dates."""
    entries = []
    current = None
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        m = HEADING_RE.match(line)
        if m:
            current = None
            if len(m.group(1)) != 2:
                continue
            words = m.group(2).split(maxsplit=1)
            if not words or words[0] not in KEYWORDS:
                continue
            title = words[1] if len(words) > 1 else ""
            title = COOKIE_RE.sub("", TAGS_RE.sub("", title)).strip()
            current = {"keyword": words[0], "title": title,
                       "closed": None, "deadline": None,
                       "order": len(entries)}
            entries.append(current)
        elif current is not None:
            # Planning lines sit right under the heading, before the body.
            if c := CLOSED_RE.search(line):
                current["closed"] = c.group(1)
            if d := DEADLINE_RE.search(line):
                current["deadline"] = d.group(1)
    return entries


def arrange(entries):
    """Split entries into ordered groups, as (group, [entries]) pairs."""
    groups = {g: [] for g in GROUPS}
    for e in entries:
        if e["keyword"] in ("DONE", "KILL"):
            if e["closed"]:
                groups["closed"].append(e)
            else:
                print(f"skipped, {e['keyword']} without CLOSED: {e['title']}",
                      file=sys.stderr)
        else:
            groups[GROUP_OF[e["keyword"]]].append(e)
    groups["closed"].sort(key=lambda e: (e["closed"], e["order"]))
    return [(g, groups[g]) for g in GROUPS if groups[g]]


def label(e):
    """Text after the heading: the closing date, or the deadline if open."""
    if e["closed"] and e["keyword"] in ("DONE", "KILL"):
        return e["closed"]
    return e["deadline"]


def due_soon(e):
    """Whether an open heading's deadline is under a week away or past."""
    if not e["deadline"] or e["keyword"] in ("DONE", "KILL"):
        return False
    days = (date.fromisoformat(e["deadline"]) - date.today()).days
    return days < DUE_SOON_DAYS


def render(groups):
    rows = []
    y = TOP
    for i, (_, items) in enumerate(groups):
        if i:
            y += GROUP_GAP
        for e in items:
            rows.append((y, e))
            y += ROW
    line_end = y - ROW + BOTTOM - 10
    height = line_end + 20

    longest = max(len(e["title"]) + len(label(e) or "") + 2 for _, e in rows)
    width = int(TEXT_X + longest * CHAR_WIDTH + 30)

    out = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" '
        f'height="{height}" viewBox="0 0 {width} {height}" '
        f'font-family="sans-serif" font-size="{FONT_SIZE}">',
        f'<rect width="100%" height="100%" fill="#ffffff"/>',
        f'<line x1="{LINE_X}" y1="{TOP - 15}" x2="{LINE_X}" '
        f'y2="{line_end - 10}" stroke="#424242" stroke-width="2"/>',
        f'<polygon points="{LINE_X - 7},{line_end - 12} {LINE_X + 7},'
        f'{line_end - 12} {LINE_X},{line_end + 2}" fill="#424242"/>',
    ]

    for y, e in rows:
        kw = e["keyword"]
        if kw == "STRT":
            out.append(f'<circle cx="{LINE_X}" cy="{y}" r="{BALL_R}" '
                       f'fill="#ffffff" stroke="#424242" stroke-width="2"/>')
        else:
            out.append(f'<circle cx="{LINE_X}" cy="{y}" r="{DOT_R}" '
                       f'fill="{DOT_COLOR[kw]}"/>')

        color = TEXT_COLOR.get(kw, "#212121")
        weight = ' font-weight="bold"' if kw == "STRT" else ""
        strike = ' text-decoration="line-through"' if kw == "KILL" else ""
        text = (f'<text x="{TEXT_X}" y="{y + 4.5}" fill="{color}"{weight}>'
                f'<tspan{strike}>{escape(e["title"])}</tspan>')
        if when := label(e):
            fill = DUE_SOON_COLOR if due_soon(e) else DATE_COLOR
            text += (f'<tspan fill="{fill}" font-weight="normal">'
                     f': {when}</tspan>')
        out.append(text + "</text>")

    out.append("</svg>")
    return "\n".join(out) + "\n"


def main():
    root = Path(__file__).resolve().parent.parent
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("org", nargs="?", default=root / "RoadMap.org")
    ap.add_argument("-o", "--output", default=root / "RoadMap.svg")
    args = ap.parse_args()

    groups = arrange(parse(args.org))
    if not groups:
        sys.exit("no level-2 headings with a keyword found")
    Path(args.output).write_text(render(groups), encoding="utf-8")
    print(f"wrote {args.output}")


if __name__ == "__main__":
    main()
