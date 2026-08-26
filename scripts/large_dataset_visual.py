#!/usr/bin/env python3
"""Capture and audit large training and repair pages at supported viewport sizes."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any
from urllib.parse import urlencode

from large_dataset_validation import login, request_json


VIEWPORTS = (
    ("desktop", 1440, 900),
    ("compact-desktop", 1024, 768),
    ("tablet", 768, 1024),
    ("mobile", 390, 844),
)
TRAINING_ROW_SELECTOR = ".training-participant-row"
REPAIR_ROW_SELECTOR = ".repair-ledger-row"


def validate_metrics(metrics: dict[str, Any], *, row_limit: int) -> None:
    if metrics["horizontalOverflow"] > 2:
        raise AssertionError(
            f"unexpected horizontal overflow: {metrics['horizontalOverflow']}px"
        )
    if metrics["renderedRows"] > row_limit:
        raise AssertionError(
            f"rendered {metrics['renderedRows']} rows, expected no more than {row_limit}"
        )
    if metrics["domNodes"] > 2_500:
        raise AssertionError(f"excessive first-view DOM size: {metrics['domNodes']}")
    if metrics["overlappingCells"]:
        raise AssertionError(
            f"visible data cells overlap: {metrics['overlappingCells']}"
        )
    if metrics["documentHeight"] > metrics["viewportHeight"] * 12:
        raise AssertionError(
            "document height suggests that records are no longer bounded by pagination"
        )


def collect_metrics(page: Any, selector: str) -> dict[str, Any]:
    return page.evaluate(
        """
        selector => {
          const root = document.documentElement;
          const body = document.body;
          const scrollWidth = Math.max(root.scrollWidth, body?.scrollWidth || 0);
          const clientWidth = root.clientWidth;
          let overlappingCells = 0;
          document.querySelectorAll(selector).forEach(row => {
            const cells = [...row.querySelectorAll(':scope > td')]
              .slice(0, 5)
              .filter(cell => {
                const style = getComputedStyle(cell);
                const rect = cell.getBoundingClientRect();
                return style.display !== 'none' && rect.width > 0 && rect.height > 0;
              });
            for (let left = 0; left < cells.length; left += 1) {
              const a = cells[left].getBoundingClientRect();
              for (let right = left + 1; right < cells.length; right += 1) {
                const b = cells[right].getBoundingClientRect();
                const overlapWidth = Math.min(a.right, b.right) - Math.max(a.left, b.left);
                const overlapHeight = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
                if (overlapWidth > 2 && overlapHeight > 2) overlappingCells += 1;
              }
            }
          });
          return {
            viewportWidth: window.innerWidth,
            viewportHeight: window.innerHeight,
            documentHeight: Math.max(root.scrollHeight, body?.scrollHeight || 0),
            horizontalOverflow: Math.max(0, scrollWidth - clientWidth),
            renderedRows: document.querySelectorAll(selector).length,
            domNodes: document.querySelectorAll('*').length,
            overlappingCells,
            activeElement: document.activeElement?.getAttribute('name')
              || document.activeElement?.textContent?.trim().slice(0, 60)
              || document.activeElement?.tagName
          };
        }
        """,
        selector,
    )


def audit_large_dataset(
    base_url: str,
    student_no: str,
    password: str,
    from_date: str,
    to_date: str,
    screenshot_directory: str | Path,
) -> dict[str, Any]:
    try:
        from playwright.sync_api import sync_playwright
    except ImportError as error:
        raise RuntimeError("Python Playwright is required for visual validation") from error

    base_url = base_url.rstrip("/")
    token, _ = login(base_url, student_no, password)
    training_query = urlencode(
        {
            "keyword": "超长标题测试",
            "from": from_date,
            "to": to_date,
            "page": 1,
            "pageSize": 20,
        }
    )
    training_page = request_json(
        base_url, "GET", f"/api/trainings/page?{training_query}", token
    )
    if not training_page["items"]:
        raise AssertionError("large training session was not found")
    large_training_id = training_page["items"][0]["id"]

    screenshot_root = Path(screenshot_directory).resolve()
    screenshot_root.mkdir(parents=True, exist_ok=True)
    results: list[dict[str, Any]] = []
    console_issues: list[dict[str, str]] = []

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        try:
            for name, width, height in VIEWPORTS:
                context = browser.new_context(
                    viewport={"width": width, "height": height},
                    device_scale_factor=1,
                    locale="zh-CN",
                )
                context.add_init_script(
                    f"localStorage.setItem('ca_attendance_token', {json.dumps(token)});"
                )
                page = context.new_page()
                page.emulate_media(reduced_motion="reduce")
                page.on(
                    "console",
                    lambda message, viewport=name: console_issues.append(
                        {"viewport": viewport, "type": message.type, "text": message.text}
                    )
                    if message.type in {"error", "warning"}
                    else None,
                )
                page.on(
                    "pageerror",
                    lambda error, viewport=name: console_issues.append(
                        {"viewport": viewport, "type": "pageerror", "text": str(error)}
                    ),
                )

                training_route = (
                    f"{base_url}/#/admin/trainings?sessionId={large_training_id}"
                    f"&from={from_date}&to={to_date}&page=1"
                )
                page.goto(training_route, wait_until="domcontentloaded")
                page.locator(TRAINING_ROW_SELECTOR).first.wait_for(timeout=30_000)
                training_metrics = collect_metrics(page, TRAINING_ROW_SELECTOR)
                validate_metrics(training_metrics, row_limit=30)
                page.get_by_role("button", name="新建培训").click()
                page.locator('[name="training-title"]').wait_for()
                training_focus = page.evaluate(
                    "document.activeElement?.getAttribute('name')"
                )
                if training_focus != "training-title":
                    raise AssertionError(
                        f"training editor focused {training_focus!r} instead of training-title"
                    )
                page.keyboard.press("Escape")
                page.locator('[name="training-title"]').wait_for(state="detached")
                training_shot = screenshot_root / f"{name}-training.png"
                page.screenshot(path=str(training_shot), full_page=False)

                repair_route = (
                    f"{base_url}/#/admin/repairs?status=REPAIRING"
                    f"&from={from_date}&to={to_date}&page=1"
                )
                page.goto(repair_route, wait_until="domcontentloaded")
                page.locator(REPAIR_ROW_SELECTOR).first.wait_for(timeout=30_000)
                repair_metrics = collect_metrics(page, REPAIR_ROW_SELECTOR)
                validate_metrics(repair_metrics, row_limit=30)
                page.get_by_role("button", name="新建维修").click()
                page.locator('[name="repair-owner-name"]').wait_for()
                repair_focus = page.evaluate(
                    "document.activeElement?.getAttribute('name')"
                )
                if repair_focus != "repair-owner-name":
                    raise AssertionError(
                        f"repair editor focused {repair_focus!r} instead of repair-owner-name"
                    )
                page.keyboard.press("Escape")
                page.locator('[name="repair-owner-name"]').wait_for(state="detached")
                repair_shot = screenshot_root / f"{name}-repairing.png"
                page.screenshot(path=str(repair_shot), full_page=False)

                history_route = (
                    f"{base_url}/#/admin/repairs?status=COMPLETED"
                    f"&from={from_date}&to={to_date}&page=1"
                )
                page.goto(history_route, wait_until="domcontentloaded")
                page.locator(REPAIR_ROW_SELECTOR).first.wait_for(timeout=30_000)
                history_metrics = collect_metrics(page, REPAIR_ROW_SELECTOR)
                validate_metrics(history_metrics, row_limit=30)
                history_shot = screenshot_root / f"{name}-repair-history.png"
                page.screenshot(path=str(history_shot), full_page=False)

                results.append(
                    {
                        "viewport": name,
                        "size": {"width": width, "height": height},
                        "training": training_metrics,
                        "repairing": repair_metrics,
                        "repairHistory": history_metrics,
                        "focus": {
                            "trainingEditor": training_focus,
                            "repairEditor": repair_focus,
                        },
                        "screenshots": [
                            str(training_shot),
                            str(repair_shot),
                            str(history_shot),
                        ],
                    }
                )
                context.close()
        finally:
            browser.close()

    if console_issues:
        raise AssertionError(
            "browser console reported issues: "
            + json.dumps(console_issues[:10], ensure_ascii=False)
        )
    return {
        "largeTrainingId": large_training_id,
        "viewports": results,
        "consoleIssues": console_issues,
        "screenshotDirectory": str(screenshot_root),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--student-no", required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument("--from-date", required=True)
    parser.add_argument("--to-date", required=True)
    parser.add_argument("--screenshots", required=True)
    parser.add_argument("--output")
    args = parser.parse_args()
    report = audit_large_dataset(
        args.base_url,
        args.student_no,
        args.password,
        args.from_date,
        args.to_date,
        args.screenshots,
    )
    content = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        output = Path(args.output).resolve()
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(content, encoding="utf-8")
    else:
        print(content, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
