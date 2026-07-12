import argparse
from pathlib import Path

from playwright.sync_api import expect, sync_playwright


def login(page, base_url: str, account: str, password: str) -> None:
    page.goto(f"{base_url}/#/login", wait_until="networkidle")
    page.get_by_placeholder("输入后台账号或学号").fill(account)
    page.get_by_placeholder("输入密码").fill(password)
    page.get_by_role("button", name="登录后台").click()


def logout(page) -> None:
    page.get_by_role("button", name="退出").click()
    expect(page.get_by_role("heading", name="后台身份验证")).to_be_visible(timeout=15_000)


def open_profile(page) -> None:
    people_group = page.locator(".admin-primary-nav button", has_text="人员").first
    expect(people_group).to_be_visible(timeout=15_000)
    people_group.click()
    profile_tab = page.locator(".admin-subnav nav button", has_text="个人").first
    expect(profile_tab).to_be_visible(timeout=10_000)
    profile_tab.click()
    expect(page.get_by_role("heading", name="个人中心")).to_be_visible(timeout=15_000)
    page.wait_for_timeout(700)


def profile_layout(page) -> dict:
    return page.locator(".profile-grid").evaluate(
        """grid => ({
            columns: getComputedStyle(grid).gridTemplateColumns,
            cards: Array.from(grid.children).map(card => ({
                className: card.className,
                width: Math.round(card.getBoundingClientRect().width),
                left: Math.round(card.getBoundingClientRect().left)
            }))
        })"""
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Verify role-specific admin UI behavior.")
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--admin-account", required=True)
    parser.add_argument("--admin-password", required=True)
    parser.add_argument("--minister-account", required=True)
    parser.add_argument("--minister-password", required=True)
    parser.add_argument("--member-account", required=True)
    parser.add_argument("--member-password", required=True)
    parser.add_argument("--screenshot-dir", required=True)
    args = parser.parse_args()

    screenshot_dir = Path(args.screenshot_dir)
    screenshot_dir.mkdir(parents=True, exist_ok=True)
    console_errors = []

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        page = browser.new_page(viewport={"width": 1440, "height": 900})
        page.on("console", lambda message: console_errors.append(message.text) if message.type == "error" else None)
        page.on("pageerror", lambda error: console_errors.append(str(error)))

        login(page, args.base_url, args.admin_account, args.admin_password)
        expect(page.locator(".user-chip", has_text="管理员")).to_be_visible(timeout=15_000)
        open_profile(page)
        admin_layout = profile_layout(page)
        page.screenshot(path=screenshot_dir / "admin-profile.png", full_page=True)
        logout(page)

        login(page, args.base_url, args.minister_account, args.minister_password)
        expect(page.locator(".user-chip", has_text="部长")).to_be_visible(timeout=15_000)
        expect(page.locator(".admin-context-heading h1", has_text="今日")).to_be_visible(timeout=15_000)
        page.wait_for_timeout(700)
        if page.get_by_text("排班待补充", exact=True).count():
            raise AssertionError("部长今日页面仍显示排班待补充")
        if page.get_by_text("值班时段未设置", exact=True).count():
            raise AssertionError("部长今日页面仍显示值班时段设置事项")
        if page.locator(".admin-subnav nav button", has_text="排班").count():
            raise AssertionError("部长仍能看到排班入口")
        page.screenshot(path=screenshot_dir / "minister-today.png", full_page=True)
        logout(page)

        login(page, args.base_url, args.member_account, args.member_password)
        expect(page.locator(".user-chip", has_text="成员")).to_be_visible(timeout=15_000)
        expect(page.get_by_role("heading", name="个人中心")).to_be_visible(timeout=15_000)
        page.wait_for_timeout(700)
        member_layout = profile_layout(page)
        page.screenshot(path=screenshot_dir / "member-profile.png", full_page=True)

        if [card["className"] for card in admin_layout["cards"]] != [card["className"] for card in member_layout["cards"]]:
            raise AssertionError(f"个人页卡片顺序不一致: admin={admin_layout}, member={member_layout}")
        if admin_layout["columns"] != member_layout["columns"]:
            raise AssertionError(f"个人页网格列宽不一致: admin={admin_layout}, member={member_layout}")
        for admin_card, member_card in zip(admin_layout["cards"], member_layout["cards"]):
            if abs(admin_card["width"] - member_card["width"]) > 1:
                raise AssertionError(f"个人页卡片宽度不一致: admin={admin_layout}, member={member_layout}")

        browser.close()

    fatal_errors = [
        error for error in console_errors
        if "favicon" not in error.lower() and "failed to load resource" not in error.lower()
    ]
    if fatal_errors:
        raise AssertionError("Browser console/page errors:\n" + "\n".join(fatal_errors[:10]))

    print("UI_ROLE_REGRESSION_OK")


if __name__ == "__main__":
    main()
