import argparse
import os
from pathlib import Path

from playwright.sync_api import expect, sync_playwright


def main() -> None:
    parser = argparse.ArgumentParser(description="Smoke-test the desktop-only administrator recovery UI.")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--account", default=os.environ.get("CA_TEST_ADMIN_STUDENT_NO", ""))
    parser.add_argument("--password", default=os.environ.get("CA_TEST_ADMIN_PASSWORD", ""))
    parser.add_argument("--screenshot", default="frontend/ui-check/desktop-recovery.png")
    args = parser.parse_args()

    if not args.account or not args.password:
        raise SystemExit(
            "Provide --account/--password or CA_TEST_ADMIN_STUDENT_NO/CA_TEST_ADMIN_PASSWORD."
        )

    screenshot = Path(args.screenshot)
    screenshot.parent.mkdir(parents=True, exist_ok=True)

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        context = browser.new_context(viewport={"width": 1440, "height": 980})
        context.add_init_script(
            """
            window.desktopAPI = {
              isDesktop: true,
              resetAdminPassword: async request => {
                window.__desktopRecoveryRequest = request;
                return { account: request.account };
              }
            };
            """
        )
        page = context.new_page()
        console_errors = []
        page.on("console", lambda message: console_errors.append(message.text) if message.type == "error" else None)
        page.on("pageerror", lambda error: console_errors.append(str(error)))

        page.goto(args.base_url.rstrip("/"), wait_until="networkidle")
        page.get_by_role("button", name="进入后台").click()
        trigger = page.get_by_role("button", name="本机管理员密码恢复")
        expect(trigger).to_be_visible(timeout=10_000)
        trigger.click()

        expect(page.get_by_role("heading", name="恢复管理员密码")).to_be_visible()
        page.locator("#recoveryAccount").fill(args.account)
        page.locator("#recoveryPassword").fill(args.password)
        page.locator("#recoveryPasswordConfirm").fill(args.password)
        page.screenshot(path=str(screenshot), full_page=True)
        page.get_by_role("button", name="确认恢复").click()
        expect(page.get_by_text("管理员密码已恢复，请使用新密码登录")).to_be_visible(timeout=5_000)

        request = page.evaluate("window.__desktopRecoveryRequest")
        if request != {"account": args.account, "newPassword": args.password}:
            raise AssertionError(f"Unexpected desktop recovery request: {request}")
        if console_errors:
            raise AssertionError("Browser console errors:\n" + "\n".join(console_errors))

        context.close()
        browser.close()

    print("DESKTOP_RECOVERY_UI_SMOKE_OK")


if __name__ == "__main__":
    main()
