#!/usr/bin/env python3
"""Lightweight structural checks for the static Pary landing page."""
from html.parser import HTMLParser
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
PAGE = ROOT / "index.html"
VOID = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"}

class SiteParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.stack = []
        self.ids = set()
        self.anchors = []
        self.errors = []
        self.title_text = ""
        self.in_title = False
        self.has_viewport = False
        self.has_description = False
        self.has_language = False

    def handle_starttag(self, tag, attrs):
        data = dict(attrs)
        if tag == "html" and data.get("lang"):
            self.has_language = True
        if tag == "meta":
            self.has_viewport |= data.get("name") == "viewport"
            self.has_description |= data.get("name") == "description"
        if "id" in data:
            if data["id"] in self.ids:
                self.errors.append(f"duplicate id: {data['id']}")
            self.ids.add(data["id"])
        if tag == "a":
            href = data.get("href", "")
            if href.startswith("#") and len(href) > 1:
                self.anchors.append(href[1:])
            if data.get("target") == "_blank":
                rel = set(data.get("rel", "").lower().split())
                if not {"noopener", "noreferrer"}.issubset(rel):
                    self.errors.append(f"external tab missing noopener noreferrer: {href}")
        if tag == "img" and "alt" not in data:
            self.errors.append("image missing alt text")
        if tag == "title":
            self.in_title = True
        if tag not in VOID:
            self.stack.append(tag)

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)
        if tag not in VOID and self.stack and self.stack[-1] == tag:
            self.stack.pop()

    def handle_endtag(self, tag):
        if tag == "title":
            self.in_title = False
        if tag in VOID:
            self.errors.append(f"void element has closing tag: {tag}")
            return
        if not self.stack or self.stack[-1] != tag:
            self.errors.append(f"unexpected closing tag: {tag}")
            if tag in self.stack:
                self.stack = self.stack[:self.stack.index(tag)]
            return
        self.stack.pop()

    def handle_data(self, data):
        if self.in_title:
            self.title_text += data

def main():
    if not PAGE.exists():
        print("index.html is missing", file=sys.stderr)
        return 1
    source = PAGE.read_text(encoding="utf-8")
    parser = SiteParser()
    parser.feed(source)
    parser.close()
    if parser.stack:
        parser.errors.append("unclosed tags: " + ", ".join(parser.stack))
    if not parser.title_text.strip():
        parser.errors.append("page title is missing")
    if not parser.has_language:
        parser.errors.append("html lang attribute is missing")
    if not parser.has_viewport:
        parser.errors.append("responsive viewport meta is missing")
    if not parser.has_description:
        parser.errors.append("SEO description is missing")
    for anchor in parser.anchors:
        if anchor not in parser.ids:
            parser.errors.append(f"broken in-page anchor: #{anchor}")
    if not (ROOT / "favicon.svg").exists():
        parser.errors.append("favicon.svg is missing")
    if "</html>" not in source[-100:]:
        parser.errors.append("document appears truncated")
    if "prefers-reduced-motion" not in source:
        parser.errors.append("reduced-motion support is missing")
    if parser.errors:
        print("\n".join("ERROR: " + error for error in parser.errors), file=sys.stderr)
        return 1
    print(f"Site checks passed: {len(parser.ids)} unique IDs, {len(parser.anchors)} in-page links.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
