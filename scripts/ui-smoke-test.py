import argparse
import os
from pathlib import Path

from playwright.sync_api import TimeoutError as PlaywrightTimeoutError
from playwright.sync_api import expect, sync_playwright


def env_or_default(name: str, default: str = "") -> str:
    return os.environ.get(name, default)


def click_tab(page, label: str) -> None:
    tab = page.locator(".tab-row button", has_text=label).first
    expect(tab).to_be_visible(timeout=10_000)
    tab.click()
    page.wait_for_timeout(250)


def main() -> None:
    parser = argparse.ArgumentParser(description="Smoke-test the local CA attendance web UI.")
    parser.add_argument("--base-url", default=env_or_default("CA_TEST_BASE_URL", "http://127.0.0.1:8080"))
    parser.add_argument("--admin-student-no", default=env_or_default("CA_TEST_ADMIN_STUDENT_NO"))
    parser.add_argument("--admin-password", default=env_or_default("CA_TEST_ADMIN_PASSWORD"))
    parser.add_argument("--screenshot-dir", default="frontend/ui-check")
    args = parser.parse_args()

    if not args.admin_student_no or not args.admin_password:
        raise SystemExit("Provide --admin-student-no/--admin-password or CA_TEST_ADMIN_STUDENT_NO/CA_TEST_ADMIN_PASSWORD.")

    base_url = args.base_url.rstrip("/")
    screenshot_dir = Path(args.screenshot_dir)
    screenshot_dir.mkdir(parents=True, exist_ok=True)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page(viewport={"width": 1440, "height": 980})
        console_errors = []
        page.on("console", lambda msg: console_errors.append(msg.text) if msg.type == "error" else None)
        page.on("pageerror", lambda err: console_errors.append(str(err)))

        page.goto(base_url, wait_until="networkidle")
        expect(page.get_by_text("值班签到台")).to_be_visible(timeout=10_000)
        expect(page.get_by_text("签到 / 签退")).to_be_visible(timeout=10_000)
        expect(page.get_by_text("今日部长排班表")).to_be_visible(timeout=10_000)

        lookup_input = page.locator("#studentNo")
        lookup_input.fill(args.admin_student_no)
        page.locator(".lookup-form button[type='submit']").click()
        expect(page.get_by_text("查询结果")).to_be_visible(timeout=10_000)
        page.screenshot(path=str(screenshot_dir / "kiosk.png"), full_page=True)

        page.get_by_role("button", name="后台").click()
        expect(page.get_by_text("协会值班后台")).to_be_visible(timeout=10_000)
        page.get_by_placeholder("管理员账号或成员学号").fill(args.admin_student_no)
        page.get_by_placeholder("密码").fill(args.admin_password)
        page.get_by_role("button", name="登录后台").click()
        expect(page.get_by_text("今日概览")).to_be_visible(timeout=15_000)

        for label in ["概览", "审核", "记录", "成员", "统计", "培训", "排班", "维修", "数据", "维护", "设置", "日志", "个人"]:
            click_tab(page, label)

        click_tab(page, "培训")
        expect(page.get_by_role("heading", name="培训管理")).to_be_visible(timeout=10_000)
        expect(page.get_by_role("button", name="新培训")).to_be_visible(timeout=10_000)

        click_tab(page, "数据")
        expect(page.get_by_role("heading", name="数据中心")).to_be_visible(timeout=10_000)
        expect(page.get_by_text("培训导入模板")).to_be_visible(timeout=10_000)

        click_tab(page, "维修")
        expect(page.get_by_role("heading", name="维修事务")).to_be_visible(timeout=10_000)

        page.screenshot(path=str(screenshot_dir / "dashboard.png"), full_page=True)
        browser.close()

        fatal_errors = [
            item for item in console_errors
            if "favicon" not in item.lower()
            and "failed to load resource" not in item.lower()
        ]
        if fatal_errors:
            raise AssertionError("Browser console/page errors:\n" + "\n".join(fatal_errors[:10]))

    print("UI_SMOKE_TEST_OK")


if __name__ == "__main__":
    try:
        main()
    except PlaywrightTimeoutError as exc:
        raise SystemExit(f"UI smoke test timed out: {exc}") from exc
