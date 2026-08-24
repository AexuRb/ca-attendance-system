import argparse
import os
import re
import time
import zipfile
from pathlib import Path

from playwright.sync_api import (
    Browser,
    Page,
    TimeoutError as PlaywrightTimeoutError,
    expect,
    sync_playwright,
)


ADMIN_PAGES = [
    ("today", "今日"),
    ("reviews", "签到审核"),
    ("attendance", "值班记录"),
    ("stats", "值班统计"),
    ("schedules", "排班管理"),
    ("members", "成员名册"),
    ("profile", "个人资料"),
    ("repairs", "维修事务"),
    ("trainings", "培训记录"),
    ("data", "数据与备份"),
    ("settings", "系统设置"),
    ("logs", "操作日志"),
]


def env_or_default(name: str, default: str = "") -> str:
    return os.environ.get(name, default)


def admin_url(base_url: str, route: str) -> str:
    return f"{base_url}/#/admin/{route}"


def expect_route(page: Page, suffix: str) -> None:
    expect(page).to_have_url(re.compile(rf"{re.escape(suffix)}(?:\?.*)?$"))


def assert_no_page_overflow(page: Page, label: str) -> None:
    metrics = page.evaluate(
        """() => ({
            clientWidth: document.documentElement.clientWidth,
            scrollWidth: document.documentElement.scrollWidth
        })"""
    )
    if metrics["scrollWidth"] > metrics["clientWidth"] + 2:
        raise AssertionError(
            f"{label} page has horizontal overflow: "
            f"scrollWidth={metrics['scrollWidth']}, "
            f"clientWidth={metrics['clientWidth']}"
        )


def assert_xlsx(download, label: str) -> None:
    path = download.path()
    if not path or not zipfile.is_zipfile(path):
        raise AssertionError(f"{label} did not produce a valid xlsx file")


def go_admin(page: Page, base_url: str, route: str, heading: str) -> None:
    page.goto(admin_url(base_url, route), wait_until="networkidle")
    expect(page.get_by_role("heading", name=heading, exact=True)).to_be_visible(
        timeout=15_000
    )
    expect_route(page, f"/#/admin/{route}")


def screenshot(page: Page, directory: Path, name: str, full_page: bool = True) -> None:
    page.screenshot(path=str(directory / name), full_page=full_page)


def initialize_system(
    page: Page,
    base_url: str,
    account: str,
    password: str,
    name: str,
    screenshot_dir: Path,
) -> None:
    page.goto(base_url, wait_until="networkidle")
    expect(page.get_by_role("heading", name="初始化本机", exact=True)).to_be_visible(
        timeout=15_000
    )
    expect_route(page, "/#/setup")
    screenshot(page, screenshot_dir, "setup-desktop.png")

    page.set_viewport_size({"width": 390, "height": 844})
    assert_no_page_overflow(page, "mobile setup")
    screenshot(page, screenshot_dir, "setup-mobile.png")
    page.set_viewport_size({"width": 1440, "height": 980})

    page.locator("input[name='account']").fill(account)
    page.locator("input[name='name']").fill(name)
    page.locator("input[name='password']").fill(password)
    page.locator("input[name='confirmation']").fill(password)
    page.get_by_role("button", name="创建本地系统", exact=True).click()
    expect(page.get_by_role("heading", name="今日", exact=True)).to_be_visible(
        timeout=15_000
    )
    expect_route(page, "/#/admin/today")

    page.get_by_role("button", name="退出登录", exact=True).click()
    expect(page.get_by_role("heading", name="登录后台", exact=True)).to_be_visible(
        timeout=15_000
    )
    page.get_by_role("link", name="返回签到台", exact=True).click()
    expect(page.get_by_role("heading", name="签到 / 签退", exact=True)).to_be_visible(
        timeout=15_000
    )


def verify_kiosk(
    page: Page,
    account: str,
    admin_name: str,
    screenshot_dir: Path,
) -> None:
    expect_route(page, "/#/")
    expect(
        page.get_by_role("heading", name="今日部长排班", exact=True)
    ).to_be_visible()
    lookup_input = page.locator("#member-query")
    expect(lookup_input).to_be_focused(timeout=10_000)

    lookup_attempts = {"count": 0}

    def retry_lookup(route) -> None:
        lookup_attempts["count"] += 1
        if lookup_attempts["count"] == 1:
            route.abort("failed")
            return
        route.fulfill(
            status=200,
            content_type="application/json",
            body=(
                '{"exists":false,"dutyDay":true,"withinDutyPeriod":true,'
                '"message":"未找到该成员","matches":[]}'
            ),
        )

    page.route("**/api/public/attendance/lookup?*", retry_lookup)
    lookup_input.fill("断线保留测试")
    page.get_by_role("button", name="继续", exact=True).click()
    expect(page.get_by_role("alert")).to_contain_text(
        "已保留当前输入", timeout=10_000
    )
    expect(lookup_input).to_have_value("断线保留测试")
    expect(page.get_by_role("alert")).to_contain_text(
        "请检查学号", timeout=8_000
    )
    if lookup_attempts["count"] < 2:
        raise AssertionError("offline lookup was not retried automatically")
    page.unroute("**/api/public/attendance/lookup?*")

    def same_name_lookup(route) -> None:
        route.fulfill(
            status=200,
            content_type="application/json",
            body=(
                '{"exists":false,"message":"找到多位同名成员",'
                '"matches":['
                '{"memberToken":"member-a","maskedStudentNo":"******1224",'
                '"name":"同名测试","grade":"2025级"},'
                '{"memberToken":"member-b","maskedStudentNo":"******8877",'
                '"name":"同名测试","grade":"2024级"}]}'
            ),
        )

    lookup_input.fill("同名测试")
    page.route("**/api/public/attendance/lookup?*", same_name_lookup)
    page.get_by_role("button", name="继续", exact=True).click()
    expect(page.get_by_role("heading", name="选择账号", exact=True)).to_be_visible()
    expect(page.get_by_text("******1224", exact=False)).to_be_visible()
    expect(page.get_by_text("******8877", exact=False)).to_be_visible()
    expect(page.get_by_text("8800001224", exact=True)).to_have_count(0)
    page.get_by_role("button", name="重新输入", exact=True).click()
    page.unroute("**/api/public/attendance/lookup?*")

    lookup_input.fill(account)
    page.get_by_role("button", name="继续", exact=True).click()
    expect(page.get_by_role("heading", name="确认身份", exact=True)).to_be_visible(
        timeout=10_000
    )
    expect(page.get_by_text(admin_name, exact=True)).to_be_visible()
    page.get_by_role("button", name="确认签到", exact=True).click()
    expect(
        page.get_by_role("heading", name=f"{admin_name}，签到成功", exact=True)
    ).to_be_visible(timeout=10_000)
    screenshot(page, screenshot_dir, "kiosk-success.png")

    expect(lookup_input).to_be_visible(timeout=7_000)
    expect(lookup_input).to_be_focused(timeout=2_000)
    lookup_input.fill(account)
    page.get_by_role("button", name="继续", exact=True).click()
    expect(page.get_by_role("button", name="确认签退", exact=True)).to_be_visible(
        timeout=10_000
    )
    page.get_by_role("button", name="确认签退", exact=True).click()
    expect(
        page.get_by_role("heading", name=f"{admin_name}，签退成功", exact=True)
    ).to_be_visible(timeout=10_000)
    page.get_by_role("button", name="下一位", exact=True).click()
    expect(lookup_input).to_be_focused(timeout=2_000)

    page.set_viewport_size({"width": 390, "height": 844})
    assert_no_page_overflow(page, "mobile kiosk")
    screenshot(page, screenshot_dir, "kiosk-mobile.png")
    page.set_viewport_size({"width": 1440, "height": 980})
    screenshot(page, screenshot_dir, "kiosk-desktop.png")


def login_admin(
    page: Page,
    account: str,
    password: str,
    screenshot_dir: Path,
) -> None:
    page.get_by_role("link", name="后台", exact=True).click()
    expect(page.get_by_role("heading", name="登录后台", exact=True)).to_be_visible(
        timeout=10_000
    )
    expect_route(page, "/#/login")
    expect(page.get_by_role("link", name="返回签到台", exact=True)).to_be_visible()

    account_input = page.locator("input[name='username']")
    password_input = page.locator("input[name='password']")
    account_input.fill(account)
    password_input.fill(password)
    expect(password_input).to_have_attribute("type", "password")
    page.get_by_role("button", name="显示密码", exact=True).click()
    expect(password_input).to_have_attribute("type", "text")
    page.get_by_role("button", name="隐藏密码", exact=True).click()
    expect(password_input).to_have_attribute("type", "password")
    page.get_by_role("checkbox").check()

    page.set_viewport_size({"width": 390, "height": 844})
    assert_no_page_overflow(page, "mobile login")
    screenshot(page, screenshot_dir, "login-mobile.png")
    page.set_viewport_size({"width": 1440, "height": 980})

    page.get_by_role("button", name="进入后台", exact=True).click()
    expect(page.get_by_role("heading", name="今日", exact=True)).to_be_visible(
        timeout=15_000
    )
    expect_route(page, "/#/admin/today")
    remembered = page.evaluate(
        "() => localStorage.getItem('ca_remembered_account')"
    )
    if remembered != account:
        raise AssertionError(f"remembered account mismatch: {remembered!r}")
    storage = page.evaluate(
        """() => ({
            localToken: localStorage.getItem('ca_attendance_token'),
            sessionToken: sessionStorage.getItem('ca_attendance_token'),
            legacyCredentials: localStorage.getItem('ca_remembered_credentials')
        })"""
    )
    if not storage["localToken"] or storage["sessionToken"]:
        raise AssertionError("local login token was not stored only in localStorage")
    if storage["legacyCredentials"] is not None:
        raise AssertionError("legacy remembered credentials were not removed")
    screenshot(page, screenshot_dir, "admin-today.png")


def verify_unsaved_repair(page: Page, base_url: str) -> None:
    go_admin(page, base_url, "repairs", "维修事务")
    page.get_by_role("button", name="新建维修", exact=True).click()
    expect(
        page.get_by_role("heading", name="新建维修事务", exact=True)
    ).to_be_visible()
    owner = page.locator("input[name='repair-owner-name']")
    owner.fill("未保存测试")
    page.evaluate("() => { window.location.hash = '#/admin/today'; }")

    confirm = page.locator(
        "[role='dialog']", has_text="当前维修事务还有未保存的内容"
    )
    expect(confirm).to_be_visible(timeout=10_000)
    confirm.get_by_role("button", name="取消", exact=True).click()
    expect(confirm).not_to_be_visible(timeout=5_000)
    expect(owner).to_have_value("未保存测试")
    expect_route(page, "/#/admin/repairs")

    page.evaluate("() => { window.location.hash = '#/admin/today'; }")
    expect(confirm).to_be_visible(timeout=10_000)
    confirm.get_by_role("button", name="放弃修改", exact=True).click()
    expect(page.get_by_role("heading", name="今日", exact=True)).to_be_visible(
        timeout=15_000
    )


def verify_exports(page: Page, base_url: str) -> None:
    go_admin(page, base_url, "stats", "值班统计")
    with page.expect_download(timeout=20_000) as download_info:
        page.get_by_role("button", name="导出 Excel", exact=True).click()
    assert_xlsx(download_info.value, "statistics export")

    go_admin(page, base_url, "data", "数据与备份")
    expect(page.get_by_role("heading", name="选择数据源", exact=True)).to_be_visible()
    for heading in ["设置筛选条件", "选择导出字段", "预览与导出"]:
        page.get_by_role("button", name="下一步", exact=True).click()
        expect(page.get_by_role("heading", name=heading, exact=True)).to_be_visible()
    page.get_by_role("button", name="生成预览", exact=True).click()
    expect(page.locator(".preview-table")).to_be_visible(timeout=15_000)
    with page.expect_download(timeout=20_000) as download_info:
        page.get_by_role("button", name="导出 Excel", exact=True).click()
    assert_xlsx(download_info.value, "custom export")

    go_admin(page, base_url, "logs", "操作日志")
    with page.expect_download(timeout=20_000) as download_info:
        page.get_by_role("button", name="导出日志", exact=True).click()
    assert_xlsx(download_info.value, "operation log export")


def verify_admin_pages(
    page: Page,
    base_url: str,
    screenshot_dir: Path,
) -> None:
    for route, heading in ADMIN_PAGES:
        go_admin(page, base_url, route, heading)
        assert_no_page_overflow(page, f"desktop {route}")
        if route in {"members", "trainings", "repairs", "data"}:
            screenshot(page, screenshot_dir, f"admin-{route}.png")

    page.set_viewport_size({"width": 390, "height": 844})
    for route, heading in [
        ("today", "今日"),
        ("members", "成员名册"),
        ("repairs", "维修事务"),
        ("data", "数据与备份"),
    ]:
        go_admin(page, base_url, route, heading)
        assert_no_page_overflow(page, f"mobile {route}")
    screenshot(page, screenshot_dir, "admin-mobile.png")
    page.set_viewport_size({"width": 1440, "height": 980})


def verify_first_password_change(
    page: Page,
    base_url: str,
) -> None:
    forced_student_no = f"88{int(time.time() * 1000) % 10_000_000_000:010d}"
    create_result = page.evaluate(
        """async ({ studentNo }) => {
            const token = localStorage.getItem('ca_attendance_token');
            const response = await fetch('/api/users', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    Authorization: `Bearer ${token}`
                },
                body: JSON.stringify({
                    studentNo,
                    name: '首次改密测试',
                    role: 'MEMBER',
                    phone: '',
                    major: '',
                    grade: '',
                    qq: ''
                })
            });
            return { status: response.status, text: await response.text() };
        }""",
        {"studentNo": forced_student_no},
    )
    if create_result["status"] not in {200, 201}:
        raise AssertionError(f"failed to create forced-password user: {create_result}")

    page.get_by_role("button", name="退出登录", exact=True).click()
    expect(page.get_by_role("heading", name="登录后台", exact=True)).to_be_visible()
    page.locator("input[name='username']").fill(forced_student_no)
    page.locator("input[name='password']").fill(forced_student_no[-6:])
    page.get_by_role("button", name="进入后台", exact=True).click()
    expect(page.get_by_role("heading", name="设置新密码", exact=True)).to_be_visible(
        timeout=15_000
    )
    expect_route(page, "/#/password")

    new_password = f"UiSmoke-{forced_student_no[-6:]}"
    page.locator("input[name='oldPassword']").fill(forced_student_no[-6:])
    page.locator("input[name='newPassword']").fill(new_password)
    page.locator("input[name='confirmation']").fill(new_password)
    page.get_by_role("button", name="更新密码", exact=True).click()
    expect(page.get_by_role("heading", name="登录后台", exact=True)).to_be_visible(
        timeout=15_000
    )

    page.locator("input[name='username']").fill(forced_student_no)
    page.locator("input[name='password']").fill(new_password)
    page.get_by_role("button", name="进入后台", exact=True).click()
    expect(page.get_by_role("heading", name="个人资料", exact=True)).to_be_visible(
        timeout=15_000
    )
    expect_route(page, "/#/admin/profile")


def verify_remote_entry(
    browser: Browser,
    remote_base_url: str,
    account: str,
    password: str,
    screenshot_dir: Path,
) -> None:
    context = browser.new_context(viewport={"width": 1440, "height": 900})
    context.add_init_script(
        "() => localStorage.setItem('ca_attendance_token', 'stale-browser-session')"
    )
    page = context.new_page()
    page.goto(remote_base_url, wait_until="networkidle")
    expect(page.get_by_role("heading", name="登录后台", exact=True)).to_be_visible(
        timeout=15_000
    )
    expect_route(page, "/#/login")
    expect(page.get_by_role("link", name="返回签到台", exact=True)).to_have_count(0)
    if page.evaluate("() => localStorage.getItem('ca_attendance_token')") is not None:
        raise AssertionError("remote entry did not remove a legacy persistent token")

    page.locator("input[name='username']").fill(account)
    page.locator("input[name='password']").fill(password)
    page.get_by_role("button", name="进入后台", exact=True).click()
    expect(page.get_by_role("heading", name="今日", exact=True)).to_be_visible(
        timeout=15_000
    )
    storage = page.evaluate(
        """() => ({
            localToken: localStorage.getItem('ca_attendance_token'),
            sessionToken: sessionStorage.getItem('ca_attendance_token')
        })"""
    )
    if storage["localToken"] or not storage["sessionToken"]:
        raise AssertionError("remote login token was not stored only in sessionStorage")
    public_status = page.evaluate(
        """async () => {
            const response = await fetch('/api/public/attendance/lookup?query=test');
            return response.status;
        }"""
    )
    if public_status < 400:
        raise AssertionError("remote entry exposed the public attendance API")
    assert_no_page_overflow(page, "remote admin")
    screenshot(page, screenshot_dir, "remote-admin.png")
    context.close()


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Smoke-test the current CA attendance web UI."
    )
    parser.add_argument(
        "--base-url",
        default=env_or_default("CA_TEST_BASE_URL", "http://127.0.0.1:8080"),
    )
    parser.add_argument(
        "--remote-base-url",
        default=env_or_default("CA_TEST_REMOTE_BASE_URL"),
    )
    parser.add_argument(
        "--admin-student-no",
        default=env_or_default("CA_TEST_ADMIN_STUDENT_NO"),
    )
    parser.add_argument(
        "--admin-password",
        default=env_or_default("CA_TEST_ADMIN_PASSWORD"),
    )
    parser.add_argument(
        "--admin-name",
        default=env_or_default("CA_TEST_ADMIN_NAME", "UI 测试管理员"),
    )
    parser.add_argument("--screenshot-dir", default="frontend/ui-check")
    args = parser.parse_args()

    if not args.admin_student_no or not args.admin_password:
        raise SystemExit(
            "Provide --admin-student-no/--admin-password or "
            "CA_TEST_ADMIN_STUDENT_NO/CA_TEST_ADMIN_PASSWORD."
        )

    base_url = args.base_url.rstrip("/")
    remote_base_url = args.remote_base_url.rstrip("/")
    screenshot_dir = Path(args.screenshot_dir)
    screenshot_dir.mkdir(parents=True, exist_ok=True)

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        page = browser.new_page(viewport={"width": 1440, "height": 980})
        console_errors: list[str] = []
        page.on(
            "console",
            lambda message: (
                console_errors.append(message.text)
                if message.type == "error"
                else None
            ),
        )
        page.on("pageerror", lambda error: console_errors.append(str(error)))

        initialize_system(
            page,
            base_url,
            args.admin_student_no,
            args.admin_password,
            args.admin_name,
            screenshot_dir,
        )
        verify_kiosk(
            page,
            args.admin_student_no,
            args.admin_name,
            screenshot_dir,
        )
        login_admin(
            page,
            args.admin_student_no,
            args.admin_password,
            screenshot_dir,
        )
        verify_unsaved_repair(page, base_url)
        verify_exports(page, base_url)
        verify_admin_pages(page, base_url, screenshot_dir)
        verify_first_password_change(page, base_url)
        if remote_base_url:
            verify_remote_entry(
                browser,
                remote_base_url,
                args.admin_student_no,
                args.admin_password,
                screenshot_dir,
            )
        browser.close()

        fatal_errors = [
            item
            for item in console_errors
            if "favicon" not in item.lower()
            and "failed to load resource" not in item.lower()
            and "net::err_failed" not in item.lower()
        ]
        if fatal_errors:
            raise AssertionError(
                "Browser console/page errors:\n" + "\n".join(fatal_errors[:10])
            )

    print(
        "UI_SMOKE_TEST_OK "
        f"pages={len(ADMIN_PAGES)} "
        f"remote={bool(remote_base_url)}"
    )


if __name__ == "__main__":
    try:
        main()
    except PlaywrightTimeoutError as exc:
        raise SystemExit(f"UI smoke test timed out: {exc}") from exc
