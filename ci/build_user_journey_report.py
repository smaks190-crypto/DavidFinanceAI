#!/usr/bin/env python3
"""Create an evidence-based, browsable UX report from real emulator captures.

Optional vision review uses an explicit API key; without it the report NEVER
pretends to have inspected screenshot pixels with AI.
"""
from __future__ import annotations

import argparse
import base64
import html
import json
import os
from pathlib import Path
import re
import shutil
import struct
import urllib.error
import urllib.request

STEPS = {
    "01-profile-picker": "Первый запуск: выбор профиля",
    "02-create-profile": "Создание тестового профиля",
    "03-new-budget-dashboard": "Главная без операций",
    "04-add-expense-empty": "Форма нового расхода",
    "05-add-expense-filled": "Заполненный расход до сохранения",
    "06-dashboard-after-expense": "Главная после сохранения",
    "07-debts-empty": "Раздел долгов до добавления записи",
    "08-add-debt-empty": "Форма нового долга",
    "09-add-debt-filled": "Заполненная форма долга",
    "10-debt-created": "Созданный долг",
    "11-goals-empty": "Раздел целей до добавления записи",
    "12-add-goal-empty": "Форма новой цели",
    "13-add-goal-filled": "Заполненная форма цели",
    "14-goal-created": "Созданная цель",
    "15-annual-report": "Годовой отчёт",
    "16-return-to-dashboard": "Возврат на главную",
    "17-david-chat": "Чат Давида",
    "18-david-read-only-financial-answer": "Ответ Давида по данным бюджета",
    "19-settings-hub": "Главный экран настроек",
    "20-settings-general": "Основные настройки",
    "99-failure": "Экран в момент ошибки теста",
}


def png_dimensions(file: Path) -> tuple[int, int]:
    with file.open("rb") as stream:
        header = stream.read(24)
    if len(header) != 24 or not header.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError("not a valid PNG header")
    return struct.unpack(">II", header[16:24])


def possible_accessibility_issues(semantics: str) -> list[str]:
    """Only report conservative review candidates, never assert WCAG failures."""
    candidates: list[str] = []
    # Each Compose node begins with 'Node #'. Only evaluate its own attributes,
    # not those of children, to avoid attributing child labels to the wrong node.
    nodes = re.split(r"(?=Node #\d+ at )", semantics)
    unnamed = 0
    for node in nodes:
        if not re.search(r"Actions\s*=\s*\[[^\]]*OnClick", node):
            continue
        first = node.split("\n", 1)[0]
        # A clickable parent can inherit labels via merge semantics; mark only
        # as a candidate for manual verification, not a confirmed defect.
        if not re.search(r"Text\s*=|ContentDescription\s*=|Role\s*=", node):
            unnamed += 1
    if unnamed:
        candidates.append(
            f"Есть {unnamed} кликабельных узлов без явного Text/ContentDescription "
            "в их блоке semantics. Проверить доступное имя в merged tree и TalkBack."
        )
    return candidates


def review_with_vision(name: str, image: Path, semantics: str) -> str:
    key = os.getenv("VISUAL_AUDIT_API_KEY", "").strip()
    if not key:
        return ""
    url = os.getenv(
        "VISUAL_AUDIT_API_URL", "https://api.openai.com/v1/chat/completions"
    )
    model = os.getenv("VISUAL_AUDIT_MODEL", "gpt-4.1-mini")
    if not url.startswith("https://"):
        raise ValueError("VISUAL_AUDIT_API_URL must be HTTPS")
    prompt = (
        "Ты UX-аудитор Android-финансового приложения. Оцени ТОЛЬКО приложенный "
        "реальный скриншот эмулятора, учитывая шаг сценария: "
        + STEPS.get(name, name)
        + ". Это тестовые данные. Найди до 3 конкретных неудобств, "
        "визуальных недочетов или рисков доступности: читаемость, иерархия, "
        "пустоты, контраст, перекрытия, элементы управления, понятность следующего шага. "
        "Для каждого укажи приоритет (высокий/средний/низкий), что видно, "
        "почему это неудобно и точное предложение исправления. "
        "Не придумывай невидимые ошибки и не выдавай гипотезы за факты. "
        "Если очевидных проблем нет, так и скажи. "
        "Не предлагай изменения кода без подтверждения исходниками. "
        "Дополнительные Compose semantics (могут быть неполными):\n"
        + semantics[:12_000]
    )
    encoded = base64.b64encode(image.read_bytes()).decode("ascii")
    payload = {
        "model": model,
        "temperature": 0.2,
        "max_tokens": 650,
        "messages": [
            {"role": "user", "content": [
                {"type": "text", "text": prompt},
                {"type": "image_url", "image_url": {
                    "url": "data:image/png;base64," + encoded,
                    "detail": "high",
                }},
            ]}
        ],
    }
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Authorization": "Bearer " + key,
                 "Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=90) as response:
        data = json.load(response)
    content = data["choices"][0]["message"]["content"]
    return content if isinstance(content, str) else str(content)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--screenshots", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--require-complete", action="store_true",
                        help="Fail after writing the report if an expected capture is missing.")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    images = args.output / "images"
    images.mkdir(exist_ok=True)

    source = args.screenshots
    shots = sorted(source.rglob("*.png")) if source.exists() else []
    md = [
        "# David FinanceAI — пользовательский визуальный аудит",
        "",
        "Скриншоты получены в Android-эмуляторе из сквозного теста. "
        "Профиль и расходы синтетические. Оценка ниже относится только к зафиксированным экранам.",
        "",
    ]
    html_cards = []
    reviewed = 0
    successful = 0
    valid_captures: set[str] = set()
    captures_with_semantics: set[str] = set()
    all_findings = []
    ai_findings = []
    for png in shots:
        name = png.stem
        title = STEPS.get(name, name)
        try:
            width, height = png_dimensions(png)
            if width < 320 or height < 320:
                raise ValueError(f"unexpected size {width}x{height}")
        except (ValueError, OSError) as error:
            all_findings.append(f"Скриншот {name} поврежден: {error}")
            continue
        output_image = images / png.name
        shutil.copy2(png, output_image)
        valid_captures.add(name)
        semantics_file = png.with_suffix(".txt")
        semantics = semantics_file.read_text(encoding="utf-8", errors="replace") if semantics_file.exists() else ""
        if semantics_file.exists():
            shutil.copy2(semantics_file, images / semantics_file.name)
        if semantics.strip():
            captures_with_semantics.add(name)
        notes = possible_accessibility_issues(semantics) if semantics else []
        ai_text = ""
        if os.getenv("VISUAL_AUDIT_API_KEY"):
            reviewed += 1
            try:
                ai_text = review_with_vision(name, png, semantics)
                successful += 1
                if ai_text:
                    ai_findings.append((name, title, ai_text))
            except (urllib.error.URLError, ValueError, KeyError, TimeoutError, OSError) as error:
                ai_text = f"ИИ-анализ этого экрана недоступен: {type(error).__name__}: {error}"
        md.extend([
            f"## {name} — {title}",
            "",
            f"![{title}](images/{png.name})",
            "",
            f"Размер: {width} × {height} px.",
            "",
        ])
        if ai_text:
            md.extend(["### Разбор изображения", "", ai_text, ""])
        else:
            md.extend([
                "**ИИ-визуальный разбор не запускался:** не настроен "
                "секрет VISUAL_AUDIT_API_KEY. Скриншот доступен для ручной оценки.",
                "",
            ])
        if notes:
            md.extend(["### Требует проверки по semantics", ""])
            md.extend(["- " + note for note in notes])
            md.append("")
        safe_title = html.escape(title)
        safe_notes = "".join(f"<li>{html.escape(n)}</li>" for n in notes)
        html_cards.append(
            f'<section><h2>{html.escape(name)} — {safe_title}</h2>'
            f'<img loading="lazy" alt="{safe_title}" src="images/{html.escape(png.name)}">'
            f'<p>{width} × {height} px</p>'
            f'<div class="notes">{html.escape(ai_text or "ИИ-оценка не настроена.")}</div>'
            f'<ul>{safe_notes}</ul></section>'
        )

    expected = set(STEPS) - {"99-failure"}
    missing_captures = sorted(expected - valid_captures)
    missing_semantics = sorted((expected & valid_captures) - captures_with_semantics)
    if missing_captures:
        all_findings.append("Нет пригодных кадров для шагов: " + ", ".join(missing_captures))
    if missing_semantics:
        all_findings.append("Нет дерева Compose semantics для шагов: " + ", ".join(missing_semantics))
    if not valid_captures:
        md.extend([
            "## Нет скриншотов", "",
            "Сквозной тест не сохранил снимки: проверь журнал CI, "
            "ошибку запуска эмулятора и путь к файлам.", "",
        ])
    if all_findings:
        md.extend(["## Ошибки артефактов", "", *("- " + issue for issue in all_findings), ""])
    coverage = f"{len(expected & valid_captures)} из {len(expected)} шагов со скриншотами"
    if "99-failure" in valid_captures:
        coverage += " · есть отдельный кадр ошибки"
    md.extend([
        "## Ограничения", "",
        "Это проход по основным экранам на одном эмуляторе, "
        "а не доказательство отсутствия UX-ошибок. "
        "Без включённого vision API скрипт не делает субъективных "
        "выводов о дизайне. Проверки TalkBack, реального API и "
        "других размеров устройств требуют отдельного запуска.",
        "",
        f"Покрытие маршрута: **{coverage}**. "
        f"Успешных ИИ-разборов: **{successful} из {reviewed} запрошенных**.",
    ])
    (args.output / "report.md").write_text("\n".join(md) + "\n", encoding="utf-8")
    page = (
        '<!doctype html><html lang="ru"><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width,initial-scale=1">'
        '<title>David FinanceAI — визуальный аудит</title>'
        '<style>body{margin:0;background:#101519;color:#f2f5f7;font:16px system-ui}'
        'header{padding:24px;max-width:1100px;margin:auto}'
        'main{display:grid;grid-template-columns:repeat(auto-fit,minmax(310px,1fr));'
        'gap:20px;max-width:1400px;margin:auto;padding:20px}'
        'section{background:#1a2328;padding:16px;border-radius:14px}'
        'img{width:100%;max-height:820px;object-fit:contain}'
        '.notes{white-space:pre-wrap;line-height:1.5}h2{font-size:18px}</style>'
        '<header><h1>David FinanceAI — визуальный аудит</h1>'
        '<p>Скриншоты реального прохождения на эмуляторе. '
        f'Ниже показаны только подтверждённые снимками экраны.</p><p>{coverage}.</p></header>'
        '<main>' + "".join(html_cards) + '</main></html>'
    )
    (args.output / "index.html").write_text(page, encoding="utf-8")
    summary = os.getenv("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as out:
            out.write("## Пользовательский визуальный аудит\n\n")
            out.write(
                f"Покрытие маршрута: **{coverage}** · ИИ-разборов: **{successful}**.\n\n"
                "Скачай **Artifacts → user-journey-visual-audit**, "
                "открой `index.html` для галереи и `report.md` для замечаний.\n\n"
            )
            if ai_findings:
                out.write("\n### Предложения по экранам\n\n")
                for filename, step_title, suggestion in ai_findings:
                    out.write(f"#### {filename} — {step_title}\n\n{suggestion}\n\n")
            if all_findings:
                out.write("### Ошибки артефактов\n\n")
                out.write("\n".join("- " + issue for issue in all_findings) + "\n")
            if not os.getenv("VISUAL_AUDIT_API_KEY"):
                out.write(
                    "ИИ-разбор выполняется при ручном запуске main с опцией `vision_review` "
                    "и настроенным секретом `VISUAL_AUDIT_API_KEY`.\n"
                )
    print(f"{coverage}; {successful} AI reviews")
    if args.require_complete and (missing_captures or missing_semantics or "99-failure" in valid_captures):
        raise SystemExit("Incomplete visual audit: see report.md for capture diagnostics")


if __name__ == "__main__":
    main()
