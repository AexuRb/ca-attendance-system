import argparse
import os
import zipfile
from pathlib import Path

from playwright.sync_api import TimeoutError as PlaywrightTimeoutError
from playwright.sync_api import expect, sync_playwright


TAB_GROUPS = {
    "今日": "值班",
    "审核": "值班",
    "记录": "值班",
    "统计": "值班",
    "排班": "值班",
    "成员": "人员",
    "个人": "人员",
    "培训": "事务",
    "维修": "事务",
    "数据": "系统",
    "维护": "系统",
    "设置": "系统",
    "日志": "系统",
}


def env_or_default(name: str, default: str = "") -> str:
    return os.environ.get(name, default)


def click_tab(page, label: str) -> None:
    group_label = TAB_GROUPS[label]
    group = page.locator(".admin-primary-nav button", has_text=group_label).first
    if group.get_attribute("aria-current") != "page":
        expect(group).to_be_visible(timeout=10_000)
        group.click()
        page.wait_for_timeout(180)
    tab = page.locator(".admin-subnav nav button", has_text=label).first
    expect(tab).to_be_visible(timeout=10_000)
    tab.click()
    page.wait_for_timeout(250)


def assert_no_page_overflow(page, label: str) -> None:
    metrics = page.evaluate(
        """() => ({
            clientWidth: document.documentElement.clientWidth,
            scrollWidth: document.documentElement.scrollWidth
        })"""
    )
    if metrics["scrollWidth"] > metrics["clientWidth"] + 2:
        raise AssertionError(
            f"{label} page has horizontal overflow: "
            f"scrollWidth={metrics['scrollWidth']}, clientWidth={metrics['clientWidth']}"
        )


def main() -> None:
    parser = argparse.ArgumentParser(description="Smoke-test the local CA attendance web UI.")
    parser.add_argument("--base-url", default=env_or_default("CA_TEST_BASE_URL", "http://127.0.0.1:8080"))
    parser.add_argument("--admin-student-no", default=env_or_default("CA_TEST_ADMIN_STUDENT_NO"))
    parser.add_argument("--admin-password", default=env_or_default("CA_TEST_ADMIN_PASSWORD"))
    parser.add_argument("--admin-name", default=env_or_default("CA_TEST_ADMIN_NAME", "UI 测试管理员"))
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
        if not page.url.endswith("/#/kiosk"):
            raise AssertionError(f"kiosk route mismatch: {page.url}")
        setup_heading = page.get_by_role("heading", name="创建管理员")
        if setup_heading.is_visible():
            page.screenshot(path=str(screenshot_dir / "setup.png"), full_page=True)
            page.set_viewport_size({"width": 390, "height": 844})
            assert_no_page_overflow(page, "mobile setup")
            page.screenshot(path=str(screenshot_dir / "setup-mobile.png"), full_page=True)
            page.set_viewport_size({"width": 1440, "height": 980})

            page.locator("#setupAccount").fill(args.admin_student_no)
            page.locator("#setupName").fill(args.admin_name)
            page.locator("#setupPassword").fill(args.admin_password)
            page.locator("#setupPasswordConfirm").fill(args.admin_password)
            page.get_by_role("button", name="完成初始化").click()
            expect(page.locator("#admin-duty-title")).to_be_visible(timeout=15_000)
            page.get_by_title("退出后台").click()
            page.get_by_role("button", name="返回签到台").click()

        expect(page.locator(".kiosk-portal-brand small")).to_have_text("值班签到台", timeout=10_000)
        expect(page.get_by_text("签到 / 签退")).to_be_visible(timeout=10_000)
        expect(page.get_by_text("今日部长排班", exact=True)).to_be_visible(timeout=10_000)

        lookup_input = page.locator("#studentNo")
        expect(lookup_input).to_be_focused(timeout=10_000)

        lookup_attempts = {"count": 0}

        def retry_lookup(route):
            lookup_attempts["count"] += 1
            if lookup_attempts["count"] == 1:
                route.abort()
                return
            route.fulfill(
                status=200,
                content_type="application/json",
                body=(
                    '{"exists":false,"dutyDay":true,"withinDutyPeriod":true,'
                    '"studentNo":null,"name":null,"action":null,'
                    '"message":"未找到该学号或姓名，或账号已停用","matches":[]}'
                ),
            )

        page.route("**/api/public/attendance/lookup**", retry_lookup)
        lookup_input.fill("断线保留测试")
        page.locator(".lookup-form button[type='submit']").click()
        expect(page.get_by_text("本机服务连接中断", exact=True)).to_be_visible(timeout=10_000)
        expect(lookup_input).to_have_value("断线保留测试")
        expect(page.get_by_text("仍无法查询时，请联系管理员确认账号是否停用。", exact=True)).to_be_visible(timeout=8_000)
        if lookup_attempts["count"] < 2:
            raise AssertionError("offline lookup was not retried automatically")
        page.unroute("**/api/public/attendance/lookup**")

        page.route(
            "**/api/public/attendance/lookup**",
            lambda route: route.fulfill(
                status=200,
                content_type="application/json",
                body=(
                    '{"exists":false,"dutyDay":true,"withinDutyPeriod":true,'
                    '"studentNo":null,"name":null,"action":null,'
                    '"message":"找到多位同名成员，请选择自己的学号",'
                    '"matches":['
                    '{"studentNo":"1004231224","name":"同名测试","grade":"2025级","major":"计算机学院"},'
                    '{"studentNo":"1004998877","name":"同名测试","grade":"2024级","major":"计算机学院"}'
                    ']}'
                ),
            ),
        )
        lookup_input.fill("同名测试")
        page.locator(".lookup-form button[type='submit']").click()
        expect(page.get_by_text("学号尾号 1224", exact=True)).to_be_visible(timeout=10_000)
        expect(page.get_by_text("学号尾号 8877", exact=True)).to_be_visible(timeout=10_000)
        expect(page.get_by_text("1004231224", exact=True)).to_have_count(0)
        page.unroute("**/api/public/attendance/lookup**")

        lookup_input.fill(args.admin_student_no)
        page.locator(".lookup-form button[type='submit']").click()
        expect(page.get_by_text("查询结果")).to_be_visible(timeout=10_000)
        page.screenshot(path=str(screenshot_dir / "kiosk.png"), full_page=True)

        page.route(
            "**/api/public/attendance/submit",
            lambda route: route.fulfill(
                status=200,
                content_type="application/json",
                body='{"message":"测试签到流程完成"}',
            ),
        )
        page.locator(".kiosk-confirm-button").click()
        expect(page.get_by_role("button", name="下一位")).to_be_visible(timeout=10_000)
        page.screenshot(path=str(screenshot_dir / "kiosk-success.png"), full_page=True)
        expect(lookup_input).to_be_visible(timeout=6_000)
        expect(lookup_input).to_be_focused(timeout=2_000)

        lookup_input.fill(args.admin_student_no)
        page.locator(".lookup-form button[type='submit']").click()
        expect(page.get_by_text("查询结果")).to_be_visible(timeout=10_000)
        page.locator(".kiosk-confirm-button").click()
        expect(page.get_by_role("button", name="下一位")).to_be_visible(timeout=10_000)
        page.get_by_role("button", name="下一位").click()
        expect(lookup_input).to_be_visible(timeout=10_000)
        expect(lookup_input).to_be_focused(timeout=2_000)
        page.unroute("**/api/public/attendance/submit")

        page.set_viewport_size({"width": 390, "height": 844})
        assert_no_page_overflow(page, "mobile kiosk")
        page.screenshot(path=str(screenshot_dir / "kiosk-mobile.png"), full_page=True)
        page.set_viewport_size({"width": 1440, "height": 980})

        page.get_by_role("button", name="进入后台").click()
        expect(page.get_by_role("heading", name="后台身份验证")).to_be_visible(timeout=10_000)
        if not page.url.endswith("/#/login"):
            raise AssertionError(f"login route mismatch: {page.url}")
        page.wait_for_timeout(800)
        page.screenshot(path=str(screenshot_dir / "login.png"), full_page=True)
        expect(page.get_by_role("button", name="返回签到台")).to_be_visible(timeout=10_000)

        page.set_viewport_size({"width": 390, "height": 844})
        assert_no_page_overflow(page, "mobile login")
        page.screenshot(path=str(screenshot_dir / "login-mobile.png"), full_page=True)
        page.set_viewport_size({"width": 1440, "height": 980})

        account_input = page.get_by_placeholder("输入后台账号或学号")
        password_input = page.get_by_placeholder("输入密码")
        account_input.fill(args.admin_student_no)
        password_input.fill(args.admin_password)
        expect(password_input).to_have_attribute("type", "password")
        page.get_by_role("button", name="显示密码").click()
        expect(password_input).to_have_attribute("type", "text")
        page.get_by_role("button", name="隐藏密码").click()
        expect(password_input).to_have_attribute("type", "password")
        page.evaluate(
            """() => {
                window.__loginVerifiedSeen = false;
                const observer = new MutationObserver(() => {
                    if (document.querySelector('.login-verified-state')) {
                        window.__loginVerifiedSeen = true;
                    }
                });
                observer.observe(document.body, { childList: true, subtree: true });
                window.__loginVerifiedObserver = observer;
            }"""
        )
        page.get_by_role("button", name="登录后台").click()
        expect(page.locator("#admin-duty-title")).to_be_visible(timeout=15_000)
        verified_seen = page.evaluate(
            """() => {
                window.__loginVerifiedObserver?.disconnect();
                return window.__loginVerifiedSeen === true;
            }"""
        )
        if not verified_seen:
            raise AssertionError("login verified transition was not rendered")
        remembered_account = page.evaluate("() => localStorage.getItem('ca-attendance-remembered-account')")
        if remembered_account != args.admin_student_no:
            raise AssertionError(f"remembered login account mismatch: {remembered_account!r}")
        page.wait_for_timeout(750)
        page.screenshot(path=str(screenshot_dir / "dashboard-home.png"), full_page=True)
        if not page.url.endswith("/#/admin/today"):
            raise AssertionError(f"today route mismatch: {page.url}")

        click_tab(page, "成员")
        if not page.url.endswith("/#/admin/members"):
            raise AssertionError(f"members route mismatch: {page.url}")
        page.reload(wait_until="networkidle")
        expect(page.get_by_role("heading", name="成员管理")).to_be_visible(timeout=15_000)
        page.go_back(wait_until="networkidle")
        expect(page.locator("#admin-duty-title")).to_be_visible(timeout=15_000)

        for label in ["今日", "审核", "记录", "成员", "统计", "培训", "排班", "维修", "数据", "设置", "日志", "个人"]:
            click_tab(page, label)
            assert_no_page_overflow(page, label)
            if label in {"成员", "个人", "培训", "排班", "数据", "设置"}:
                page.screenshot(path=str(screenshot_dir / f"dashboard-{label}.png"), full_page=True)

        click_tab(page, "培训")
        expect(page.get_by_role("heading", name="培训管理")).to_be_visible(timeout=10_000)
        expect(page.get_by_role("button", name="新培训")).to_be_visible(timeout=10_000)

        click_tab(page, "数据")
        expect(page.get_by_role("heading", name="数据中心")).to_be_visible(timeout=10_000)
        expect(page.get_by_role("tab", name="自定义导出")).to_have_attribute("aria-selected", "true")
        page.get_by_role("tab", name="导入模板").click()
        expect(page.get_by_text("培训导入模板")).to_be_visible(timeout=10_000)
        page.get_by_role("tab", name="自定义导出").click()
        expect(page.locator("#exportSectionTitle")).to_have_text("自定义 Excel 导出", timeout=10_000)
        expect(page.get_by_role("button", name="选择数据源")).to_have_attribute("aria-current", "step")
        page.get_by_role("button", name="下一步").click()
        expect(page.get_by_role("heading", name="设置筛选范围")).to_be_visible(timeout=10_000)
        page.get_by_role("button", name="下一步").click()
        expect(page.get_by_role("heading", name="选择字段与顺序")).to_be_visible(timeout=10_000)
        page.get_by_role("button", name="生成预览").click()
        expect(page.get_by_role("heading", name="预览真实数据")).to_be_visible(timeout=10_000)
        expect(page.locator(".export-preview-summary")).to_be_visible(timeout=15_000)
        page.screenshot(path=str(screenshot_dir / "dashboard-data-preview.png"), full_page=True)
        page.get_by_role("button", name="确认并继续").click()
        expect(page.get_by_role("heading", name="确认并导出")).to_be_visible(timeout=10_000)
        with page.expect_download(timeout=15_000) as download_info:
            page.get_by_role("button", name="导出 成员 Excel").click()
        download_path = download_info.value.path()
        if not download_path or not zipfile.is_zipfile(download_path):
            raise AssertionError("custom export did not produce a valid xlsx file")
        page.get_by_role("tab", name="备份与恢复").click()
        expect(page.get_by_role("heading", name="完整系统备份")).to_be_visible(timeout=10_000)

        click_tab(page, "排班")
        expect(page.get_by_role("button", name="批量导入")).to_be_visible(timeout=10_000)

        click_tab(page, "维修")
        expect(page.get_by_role("heading", name="维修事务")).to_be_visible(timeout=10_000)
        expect(page.get_by_role("button", name="回收站")).to_be_visible(timeout=10_000)

        page.screenshot(path=str(screenshot_dir / "dashboard.png"), full_page=True)
        page.set_viewport_size({"width": 390, "height": 844})
        click_tab(page, "数据")
        assert_no_page_overflow(page, "mobile data center")
        page.screenshot(path=str(screenshot_dir / "dashboard-data-mobile.png"), full_page=True)
        click_tab(page, "今日")
        assert_no_page_overflow(page, "mobile home")
        page.screenshot(path=str(screenshot_dir / "dashboard-mobile.png"), full_page=True)
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
