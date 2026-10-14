"""Combine verified research records into an offline catalog; no network access."""
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]


def text(value):
    if value is None:
        return ""
    if isinstance(value, list):
        return "；".join(text(v) for v in value)
    if isinstance(value, dict):
        return "；".join(f"{k}: {text(v)}" for k, v in value.items())
    return str(value)


def load(name):
    return json.loads((ROOT / name).read_text(encoding="utf-8-sig"))


def build():
    sources = []
    for name in ["local-sources.json", "technical-sources.json", "physics-visual-sources.json"]:
        document = load(name)
        for raw in document["sources"]:
            category = {"physics": "物理依据", "physics-status": "物理依据", "visual": "官方视觉参考", "visual-reference": "官方视觉参考", "rights": "使用权说明",
                        "license": "使用权说明"}.get(raw.get("category"), raw.get("category", "其他"))
            # Physics/rights cards are documents to read, not implementation dependencies.
            priority = raw.get("priority", "P0" if raw["id"] in ["P1", "P2"] else "P2" if raw["id"].startswith("P") else "P1")
            files = raw.get("files", [])
            display_files = [v.get("url", v.get("snapshot", "")) if isinstance(v, dict) else v for v in files]
            use = raw.get("useFor", raw.get("use", ""))
            summary = raw.get("summary", text(raw.get("evidence", []))[:420])
            limits = raw.get("limits", raw.get("boundary", raw.get("migrationNotes", [])))
            access = raw.get("access", {})
            verification = raw.get("verificationStatus", text(access.get("status", "实际页面正文已读")))
            if access.get("httpStatus"):
                verification = "HTTP " + str(access["httpStatus"]) + " · " + verification
            item = {"id": raw["id"], "title": raw["title"], "category": category,
                    "priority": priority, "tags": raw.get("tags", []), "summary": text(summary),
                    "useFor": text(use), "license": text(raw.get("license", "未核定；只链接参考")),
                    "verificationStatus": verification,
                    "readScope": text(raw.get("readScope", access.get("readScope", "见原始核查记录"))),
                    "limits": text(limits), "fixedCommit": raw.get("fixedCommit", ""),
                    "files": display_files, "localPath": raw.get("localPath", ""),
                    "url": raw.get("url", ""), "repoUrl": raw.get("repoUrl", ""),
                    "credit": text(raw.get("credit", raw.get("authors", []))),
                    "sourceRecord": name, "rawRecord": raw}
            assert priority in ["P0", "P1", "P2"], item
            for link in [item["url"], item["repoUrl"]]:
                assert not link or re.match(r"https?://", link), link
            assert item["summary"] and item["useFor"] and item["readScope"], raw["id"]
            sources.append(item)
    assert len({s["id"] for s in sources}) == len(sources)
    catalog = {"schema": "white-hole-research-catalog/1", "checkedOn": "2026-10-07",
               "timezone": "Asia/Shanghai", "scope": "资料核查与方案；未实施白洞、修改skill/MOD或启动游戏",
               "recommendedRoute": "ring", "sources": sources}
    (ROOT / "resources.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2), encoding="utf-8")
    encoded = json.dumps(catalog, ensure_ascii=False).replace("<", "\\u003c").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
    html = (ROOT / "catalog.template.html").read_text(encoding="utf-8")
    assert html.count("__CATALOG_JSON__") == 1
    (ROOT / "index.html").write_text(html.replace("__CATALOG_JSON__", encoded), encoding="utf-8")
    print(json.dumps({"sources": len(sources), "categories": {c: sum(s["category"] == c for s in sources)
                                                              for c in dict.fromkeys(s["category"] for s in sources)}}, ensure_ascii=False))


if __name__ == "__main__":
    build()
