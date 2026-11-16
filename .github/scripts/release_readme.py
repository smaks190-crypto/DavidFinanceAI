"""Render the release block while preserving the rest of README."""
import argparse
import re
from pathlib import Path


def update_readme(content, version, run_number, notes, repository):
    start = "<!-- LATEST_RELEASE_START -->"
    end = "<!-- LATEST_RELEASE_END -->"
    if content.count(start) != 1 or content.count(end) != 1 or content.index(start) > content.index(end):
        raise ValueError("README must contain one correctly ordered release marker pair")
    if not re.fullmatch(r"\d+\.\d{2}", version) or not str(run_number).isdigit() or int(run_number) < 1:
        raise ValueError("Invalid release version or build number")
    if not re.fullmatch(r"[\w.-]+/[\w.-]+", repository, flags=re.ASCII):
        raise ValueError("Invalid GitHub repository")
    notes = notes.strip() or "В этом выпуске нет автоматически описанных изменений."
    if start in notes or end in notes:
        raise ValueError("Release notes must not contain README boundary markers")
    block = (
        f"{start}\n### Версия v{version} (сборка {run_number})\n\n"
        f"[📥 Скачать последнюю версию APK](https://github.com/{repository}/releases/latest/download/app-release.apk)\n\n"
        f"**Что изменилось:**\n\n{notes}\n{end}"
    )
    before, rest = content.split(start, 1)
    _, after = rest.split(end, 1)
    return before + block + after


def main():
    parser = argparse.ArgumentParser()
    for name in ("readme", "notes", "version", "run-number", "repository"):
        parser.add_argument("--" + name, required=True)
    args = parser.parse_args()
    path = Path(args.readme)
    result = update_readme(path.read_text(encoding="utf-8"), args.version, args.run_number,
                           Path(args.notes).read_text(encoding="utf-8"), args.repository)
    path.write_text(result, encoding="utf-8")


if __name__ == "__main__":
    main()
