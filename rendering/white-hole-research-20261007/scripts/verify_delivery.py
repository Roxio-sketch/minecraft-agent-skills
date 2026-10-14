"""Check saved research artifacts without fetching sources or running Minecraft."""
from collections import Counter
from hashlib import sha256
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[1]


def load(name):
    return json.loads((ROOT / name).read_text(encoding="utf-8-sig"))


def file_record(path):
    path = Path(path)
    assert path.is_file(), f"Missing file: {path}"
    body = path.read_bytes()
    return {"path": path.as_posix(), "bytes": len(body), "sha256": sha256(body).hexdigest()}


def main():
    catalog = load("resources.json")
    local = load("local-sources.json")
    physics = load("physics-visual-sources.json")
    technical = load("technical-sources.json")
    technical_evidence = load("technical-verification.json")
    qa = load("qa/report.json")
    sources = catalog["sources"]
    assert len(sources) == 23 and len({s["id"] for s in sources}) == 23
    original_sources = local["sources"] + technical["sources"] + physics["sources"]
    assert {s["id"]: s for s in original_sources} == {s["id"]: s["rawRecord"] for s in sources}
    encoded = json.dumps(catalog, ensure_ascii=False).replace("<", "\\u003c").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
    template = (ROOT / "catalog.template.html").read_text(encoding="utf-8")
    assert template.count("__CATALOG_JSON__") == 1
    assert (ROOT / "index.html").read_text(encoding="utf-8") == template.replace("__CATALOG_JSON__", encoded)

    local_files = []
    for source in local["sources"]:
        for path, expected in source["fileHashes"].items():
            record = file_record(path)
            assert record["sha256"] == expected, f"Research baseline changed: {path}"
            local_files.append({"sourceId": source["id"], **record, "matchesResearchBaseline": True})

    physics_pages = []
    formal_paths = set()
    for source in physics["sources"]:
        record = file_record(ROOT / source["localText"])
        assert record["sha256"] == source["textSha256"], source["id"]
        formal_paths.add(source["localText"])
        if source.get("metadataText"):
            formal_paths.add(source["metadataText"])
        physics_pages.append({"sourceId": source["id"], "url": source["url"],
                              "access": source["access"], "readScope": source["readScope"],
                              "savedFile": record,
                              "savedTextKind": "Original research paraphrase and access metadata; not the full remote response"})

    technical_files = []
    assert len(technical_evidence["files"]) == 27
    for evidence_id, remote in technical_evidence["files"].items():
        assert remote["httpStatus"] == 200, evidence_id
        formal_paths.add(remote["snapshot"])
        technical_files.append({"evidenceId": evidence_id, "url": remote["url"],
                                "httpStatus": remote["httpStatus"],
                                "fetchedResponseSha256": remote["sha256"],
                                "savedFile": file_record(ROOT / remote["snapshot"]),
                                "readScope": remote["readScope"]})

    links = []
    for report_name in ["研究报告.md", "existing-capabilities.md", "physics-notes.md", "technical-notes.md"]:
        body = (ROOT / report_name).read_text(encoding="utf-8")
        for target in re.findall(r"\[[^\]]*\]\(([^)]+)\)", body):
            if re.match(r"https?://", target):
                continue
            target_path = re.sub(r":\d+$", "", target.strip("<>").split("#", 1)[0])
            if not target_path:
                continue
            path = ROOT / target_path
            assert path.exists(), f"Broken link in {report_name}: {target}"
            links.append({"document": report_name, "target": target})

    assert qa["status"] == "passed" and len(qa["checks"]) == 17
    assert qa["sourceCount"] == 23 and qa["networkDependencies"] == 0 and not qa["errors"]
    delivery_names = ["研究报告.md", "index.html", "catalog.template.html", "resources.json",
                      "existing-capabilities.md", "local-sources.json", "physics-notes.md",
                      "physics-visual-sources.json", "technical-notes.md", "technical-sources.json",
                      "technical-verification.json", "source-text/fetch-metadata.json",
                      "scripts/build_catalog.py", "scripts/check_catalog.cjs", "scripts/verify_delivery.py",
                      "qa/report.json", "qa/" + qa["exportFile"]]
    delivery_names += ["qa/" + name for name in qa["screenshots"]]
    delivery_names += sorted(formal_paths)
    saved_files = []
    for name in dict.fromkeys(delivery_names):
        record = file_record(ROOT / name)
        saved_files.append({"relativePath": name, **record})

    verification = {
        "schema": "white-hole-research-verification/1",
        "checkedOn": "2026-10-07", "timezone": "Asia/Shanghai", "status": "passed",
        "scope": "Saved research catalog, notes, references and UI checks only",
        "workPerformed": "Research files created in white-hole-research-20261007; no white-hole implementation or game launch",
        "whiteHoleImplemented": False, "whiteHoleGameValidated": False,
        "skillOrModEditedByThisResearch": False,
        "sourceCounts": {"total": len(sources), "local": len(local["sources"]),
                         "technical": len(technical["sources"]), "physics": 5, "visual": 3, "rights": 2},
        "catalogCategories": dict(Counter(s["category"] for s in sources)),
        "catalogMatchesSourceRecords": True, "htmlMatchesCatalogAndTemplate": True,
        "localSourceFiles": local_files,
        "physicsAndVisualEvidence": physics_pages,
        "technicalEvidence": {"successfulReads": len(technical_files),
                              "fixedCommits": technical_evidence["fixedCommits"],
                              "commitVerification": technical_evidence["commitVerification"],
                              "files": technical_files},
        "browserQA": qa, "validLocalMarkdownLinks": links,
        "savedArtifactFiles": saved_files,
        "hashInterpretation": "fetchedResponseSha256 hashes the original fetched response. savedFile.sha256 hashes the current bounded excerpt or paraphrased research note. They are distinct evidence objects and are not expected to match.",
        "limitations": [
            "No white-hole shader, GPU performance, visual result or Minecraft runtime has been tested.",
            "Eardley and Haggard/Rovelli records are limited to abstracts/metadata; full papers were not read.",
            "Official visual-page text and captions were read; original image/video assets were not inspected or downloaded.",
            "Existing local-source hashes cover the cited 13 files, not an exhaustive before/after audit of all project or skill files.",
            "HTTP status is access evidence, not proof of full-text reading, scientific confirmation or Forge compatibility.",
            "Intermediate rejected candidate caches are not included as formal sources."
        ]
    }
    (ROOT / "verification.json").write_text(json.dumps(verification, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"status": "passed", "sources": len(sources), "localFileHashesMatched": len(local_files),
                      "technicalReads": len(technical_files), "browserChecks": len(qa["checks"]),
                      "savedArtifacts": len(saved_files)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
