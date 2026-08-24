"""Fixture-driven browser smoke test for frontend rendering and workflows.

Business API responses are intercepted in the browser. Use ui-role-regression.py
when validating role permissions against a real backend.
"""

import argparse
import json
from pathlib import Path

from playwright.sync_api import expect, sync_playwright


MEMBER_PAGE = {
    "items": [
        {
            "id": 1,
            "studentNo": "9900000001",
            "name": "测试管理员",
            "role": "ADMIN",
            "status": "ACTIVE",
            "phone": "",
            "major": "",
            "grade": "",
            "qq": "",
        },
        {
            "id": 2,
            "studentNo": "2025000001",
            "name": "陈测试",
            "role": "MEMBER",
            "status": "ACTIVE",
            "phone": "13000000000",
            "major": "计算机学院",
            "grade": "2025级",
            "qq": "10001",
        },
    ],
    "total": 2,
    "page": 1,
    "pageSize": 20,
}

ATTENDANCE_RECORDS = [
    {
        "id": 10,
        "dutyDate": "2026-07-28",
        "checkInTime": "2026-07-28T14:00:00",
        "checkOutTime": "2026-07-28T16:00:00",
        "checkInStatus": "APPROVED",
        "checkOutStatus": "APPROVED",
        "durationMinutes": 120,
        "validHours": 2,
        "effectiveStatus": "VALID",
        "source": "PUBLIC",
    },
    {
        "id": 11,
        "dutyDate": "2026-07-27",
        "checkInTime": "2026-07-27T15:00:00",
        "checkOutTime": None,
        "checkInStatus": "PENDING",
        "checkOutStatus": "NOT_SUBMITTED",
        "durationMinutes": 0,
        "validHours": 0,
        "effectiveStatus": "PENDING",
        "source": "PUBLIC",
    },
]

ATTENDANCE_PAGE = {
    "items": [
        {
            "id": 31,
            "userId": 2,
            "userRole": "MEMBER",
            "studentNo": "2025000001",
            "name": "陈测试",
            "dutyDate": "2026-07-28",
            "dutyDay": True,
            "withinDutyPeriod": True,
            "requireDutyDay": True,
            "requireDutyPeriod": True,
            "checkInTime": "2026-07-28T14:00:00",
            "checkOutTime": "2026-07-28T16:00:00",
            "checkInStatus": "APPROVED",
            "checkOutStatus": "APPROVED",
            "durationMinutes": 120,
            "effectiveStatus": "VALID",
        }
    ],
    "total": 41,
    "page": 1,
    "pageSize": 20,
}

ACCOUNT_CANDIDATES = [
    {
        "id": 1,
        "studentNo": "9900000001",
        "name": "测试管理员",
        "role": "ADMIN",
    },
    {
        "id": 2,
        "studentNo": "2025000001",
        "name": "陈测试",
        "role": "MEMBER",
    },
]

DUTY_PERIODS = [
    {
        "sortOrder": 0,
        "startTime": "16:00",
        "endTime": "18:00",
        "enabled": True,
    },
    {
        "sortOrder": 1,
        "startTime": "14:00",
        "endTime": "16:00",
        "enabled": False,
    },
]

DUTY_WEEKDAYS = [
    {
        "weekday": index,
        "weekday_name": f"星期{'一二三四五六日'[index - 1]}",
        "enabled": index <= 5,
    }
    for index in range(1, 8)
]

SCHEDULE_CANDIDATES = [
    {
        "studentNo": "2025000101",
        "name": "张部长",
        "role": "MINISTER",
    },
    {
        "studentNo": "2025000102",
        "name": "李会长",
        "role": "PRESIDENT",
    },
]

SCHEDULE_SLOTS = [
    {
        "id": 71,
        "weekday": 1,
        "weekdayName": "星期一",
        "startTime": "16:00:00",
        "endTime": "18:00:00",
        "title": "部长值班",
        "location": "协会办公室",
        "note": "",
        "enabled": True,
        "assignees": [
            {
                "studentNo": "2025000101",
                "name": "张部长",
                "sortOrder": 0,
            }
        ],
    }
]

REPAIR_CASES = [
    {
        "id": 81,
        "caseNo": "JXWX20260729-0001",
        "agreementType": "PERSONAL_DEVICE",
        "ownerName": "送修同学",
        "ownerPhone": "13800000000",
        "deviceType": "笔记本电脑",
        "deviceBrand": "测试品牌",
        "deviceModel": "测试型号",
        "accessories": "电源适配器",
        "faultDescription": "无法开机",
        "serviceDescription": "",
        "dataBackupConfirmed": True,
        "riskAcknowledged": True,
        "privacyAcknowledged": True,
        "status": "REPAIRING",
        "receivedAt": "2026-07-29T14:00:00",
        "completedAt": None,
        "handlerUserId": 1,
        "handlerName": "测试管理员",
        "remark": "",
    }
]

TRAINING_RECORDS = [
    {
        "participantId": 20,
        "sessionId": 5,
        "title": "网络基础培训",
        "trainingDate": "2026-07-26",
        "startTime": "14:00:00",
        "endTime": "15:30:00",
        "location": "协会活动室",
        "speaker": "陈测试",
        "durationHours": 1.5,
        "remark": "主讲人",
    }
]

SUMMARY_ROWS = [
    {
        "userId": 2,
        "studentNo": "2025000001",
        "name": "陈测试",
        "grade": "2025级",
        "role": "MEMBER",
        "attendanceHours": 4,
        "trainingHours": 1.5,
        "totalHours": 5.5,
        "attendanceCount": 2,
        "trainingCount": 1,
        "dutyCount": 3,
    }
]

WEEKLY_DETAIL = {
    "days": [
        {
            "dutyDate": "2026-07-27",
            "weekday": 1,
            "weekdayName": "星期一",
        },
        {
            "dutyDate": "2026-07-28",
            "weekday": 2,
            "weekdayName": "星期二",
        },
    ],
    "users": [
        {
            "userId": 2,
            "studentNo": "2025000001",
            "name": "陈测试",
            "grade": "2025级",
            "role": "MEMBER",
            "attendanceHours": 4,
            "trainingHours": 1.5,
            "totalHours": 5.5,
        }
    ],
    "cells": {
        "2026-07-27": {"2": 2},
        "2026-07-28": {"2": 2},
    },
}


def fulfill_json(route, payload) -> None:
    route.fulfill(
        status=200,
        content_type="application/json",
        body=json.dumps(payload, ensure_ascii=False),
    )


def assert_no_page_overflow(page, label: str) -> None:
    metrics = page.evaluate(
        """() => ({
            clientWidth: document.documentElement.clientWidth,
            scrollWidth: document.documentElement.scrollWidth
        })"""
    )
    if metrics["scrollWidth"] > metrics["clientWidth"] + 2:
        raise AssertionError(
            f"{label} horizontal overflow: "
            f"{metrics['scrollWidth']} > {metrics['clientWidth']}"
        )


def login(page, base_url: str, account: str, password: str) -> None:
    page.goto(f"{base_url}/#/login", wait_until="networkidle")
    page.locator("input[name='username']").fill(account)
    page.locator("input[name='password']").fill(password)
    page.locator(".auth-form button[type='submit']").click()
    expect(page.locator(".refined-admin-layout")).to_be_visible(timeout=15_000)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Validate the current member, profile and statistics UI."
    )
    parser.add_argument("--base-url", default="http://127.0.0.1:18080")
    parser.add_argument("--admin-student-no", required=True)
    parser.add_argument("--admin-password", required=True)
    parser.add_argument("--screenshot-dir", required=True)
    args = parser.parse_args()

    screenshot_dir = Path(args.screenshot_dir)
    screenshot_dir.mkdir(parents=True, exist_ok=True)
    console_errors = []

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        page = browser.new_page(viewport={"width": 1440, "height": 960})
        page.on(
            "console",
            lambda message: (
                console_errors.append(message.text)
                if message.type == "error"
                else None
            ),
        )
        page.on("pageerror", lambda error: console_errors.append(str(error)))
        login(
            page,
            args.base_url.rstrip("/"),
            args.admin_student_no,
            args.admin_password,
        )

        page.route(
            "**/api/users/page?*",
            lambda route: fulfill_json(route, MEMBER_PAGE),
        )
        page.route(
            "**/api/users/grades",
            lambda route: fulfill_json(route, ["2025级", "2024级"]),
        )
        page.goto(
            f"{args.base_url.rstrip('/')}/#/admin/members",
            wait_until="networkidle",
        )
        expect(page.get_by_role("heading", name="成员名册")).to_be_visible()
        expect(page.get_by_text("陈测试", exact=True)).to_be_visible()
        member_row = page.locator("tbody tr").filter(has_text="2025000001")
        expect(member_row).to_have_count(1)
        member_row.locator("input[type='checkbox']").check()
        expect(page.get_by_text("已选 1 人", exact=True)).to_be_visible()
        member_row.get_by_role("button", name="编辑 陈测试").click()
        expect(page.get_by_role("heading", name="编辑成员")).to_be_visible()
        expect(page.locator("input[name='name']")).to_have_value("陈测试")
        page.get_by_role("button", name="取消").click()
        assert_no_page_overflow(page, "desktop members")
        page.screenshot(
            path=str(screenshot_dir / "members-desktop.png"), full_page=True
        )

        page.route(
            "**/api/attendance/me?*",
            lambda route: fulfill_json(route, ATTENDANCE_RECORDS),
        )
        page.route(
            "**/api/trainings/me?*",
            lambda route: fulfill_json(route, TRAINING_RECORDS),
        )
        page.goto(
            f"{args.base_url.rstrip('/')}/#/admin/profile",
            wait_until="networkidle",
        )
        expect(page.get_by_role("heading", name="个人资料")).to_be_visible()
        expect(page.get_by_text("有效", exact=True)).to_be_visible()
        expect(page.get_by_text("待审核", exact=True)).to_be_visible()
        page.get_by_role("button", name="培训 1").click()
        expect(page.get_by_text("网络基础培训", exact=True)).to_be_visible()
        expect(page.get_by_text("陈测试", exact=True)).to_be_visible()
        expect(page.get_by_text("1.5 小时", exact=True)).to_be_visible()
        page.get_by_role("button", name="修改密码").click()
        expect(page.get_by_role("heading", name="修改登录密码")).to_be_visible()
        page.get_by_role("button", name="取消").click()
        assert_no_page_overflow(page, "desktop profile")
        page.screenshot(
            path=str(screenshot_dir / "profile-desktop.png"), full_page=True
        )

        page.route(
            "**/api/stats/summary?*",
            lambda route: fulfill_json(route, SUMMARY_ROWS),
        )
        page.route(
            "**/api/stats/weekly-detail?*",
            lambda route: fulfill_json(route, WEEKLY_DETAIL),
        )
        page.goto(
            f"{args.base_url.rstrip('/')}/#/admin/stats",
            wait_until="networkidle",
        )
        expect(page.get_by_role("heading", name="值班统计")).to_be_visible()
        expect(page.get_by_text("2025级", exact=True)).to_be_visible()
        page.get_by_role("button", name="本周", exact=True).click()
        expect(page.get_by_text("星期一", exact=True)).to_be_visible()
        expect(page.get_by_text("星期二", exact=True)).to_be_visible()
        expect(
            page.locator(".weekly-total-cell", has_text="5.5")
        ).to_be_visible()
        assert_no_page_overflow(page, "desktop statistics")
        page.screenshot(
            path=str(screenshot_dir / "stats-desktop.png"), full_page=True
        )

        page.route(
            "**/api/attendance/page?*",
            lambda route: fulfill_json(route, ATTENDANCE_PAGE),
        )
        page.route(
            "**/api/attendance/manual-candidates",
            lambda route: fulfill_json(route, ACCOUNT_CANDIDATES),
        )
        page.goto(
            f"{args.base_url.rstrip('/')}/#/admin/attendance",
            wait_until="networkidle",
        )
        expect(page.get_by_role("heading", name="值班记录")).to_be_visible()
        expect(page.get_by_text("共 41 条记录", exact=True)).to_be_visible()
        expect(page.get_by_text("陈测试", exact=True)).to_be_visible()
        manual_button = page.get_by_role("button", name="补录记录")
        manual_button.click()
        expect(
            page.get_by_role("textbox", name="选择补录成员")
        ).to_be_visible()
        active_inside_dialog = page.evaluate(
            """() => {
                const dialog = document.querySelector('[role="dialog"]');
                return Boolean(dialog?.contains(document.activeElement));
            }"""
        )
        if not active_inside_dialog:
            raise AssertionError("attendance dialog did not receive focus")
        page.keyboard.press("Escape")
        expect(page.get_by_role("heading", name="补录值班记录")).not_to_be_visible()
        expect(manual_button).to_be_focused()

        manual_button.click()
        expect(
            page.get_by_role("textbox", name="选择补录成员")
        ).to_be_visible()
        page.get_by_text("陈测试", exact=True).last.click()
        expect(
            page.locator(".account-picker-current", has_text="2025000001")
        ).to_be_visible()
        page.get_by_role("button", name="取消").click()
        assert_no_page_overflow(page, "desktop attendance")
        page.screenshot(
            path=str(screenshot_dir / "attendance-desktop.png"),
            full_page=True,
        )

        page.route(
            "**/api/settings/duty-periods",
            lambda route: fulfill_json(route, DUTY_PERIODS),
        )
        page.route(
            "**/api/settings/weekdays",
            lambda route: fulfill_json(route, DUTY_WEEKDAYS),
        )
        page.goto(
            f"{args.base_url.rstrip('/')}/#/admin/settings",
            wait_until="networkidle",
        )
        expect(page.get_by_role("heading", name="系统设置")).to_be_visible()
        expect(page.get_by_text("停用", exact=True)).to_be_visible()
        expect(
            page.locator(".period-editor input[type='time']").first
        ).to_have_value("16:00")
        assert_no_page_overflow(page, "desktop settings")
        page.screenshot(
            path=str(screenshot_dir / "settings-desktop.png"),
            full_page=True,
        )

        page.route(
            "**/api/schedules",
            lambda route: fulfill_json(route, SCHEDULE_SLOTS),
        )
        page.route(
            "**/api/schedules/assignee-candidates",
            lambda route: fulfill_json(route, SCHEDULE_CANDIDATES),
        )
        page.goto(
            f"{args.base_url.rstrip('/')}/#/admin/schedules",
            wait_until="networkidle",
        )
        expect(page.get_by_role("heading", name="排班管理")).to_be_visible()
        expect(page.get_by_text("张部长", exact=True)).to_be_visible()
        page.locator("button[title='编辑排班']").click()
        expect(page.get_by_label("搜索排班人员")).to_be_visible()
        expect(page.get_by_text("1 人已选", exact=True)).to_be_visible()
        page.get_by_text("李会长", exact=True).click()
        expect(page.get_by_text("2 人已选", exact=True)).to_be_visible()
        expect(page.get_by_text("签到台展示", exact=True)).to_be_visible()
        expect(page.locator(".schedule-visibility-toggle input")).to_be_checked()
        page.locator(".schedule-visibility-toggle").click()
        expect(page.locator(".schedule-visibility-toggle input")).not_to_be_checked()
        page.get_by_role("button", name="取消").click()
        assert_no_page_overflow(page, "desktop schedules")
        page.screenshot(
            path=str(screenshot_dir / "schedules-desktop.png"),
            full_page=True,
        )

        page.route(
            "**/api/repairs",
            lambda route: fulfill_json(route, REPAIR_CASES),
        )
        page.route(
            "**/api/repairs/handler-candidates",
            lambda route: fulfill_json(route, ACCOUNT_CANDIDATES),
        )
        page.goto(
            f"{args.base_url.rstrip('/')}/#/admin/repairs",
            wait_until="networkidle",
        )
        expect(page.get_by_role("heading", name="维修事务")).to_be_visible()
        page.get_by_role("button", name="新建维修").click()
        page.get_by_role("textbox", name="联系人", exact=True).fill("测试联系人")
        page.get_by_role("textbox", name="设备类型", exact=True).fill("测试电脑")
        page.get_by_role("textbox", name="故障描述", exact=True).fill("无法正常启动")
        page.get_by_role("button", name="下一步").click()
        mobile_handler_picker = page.get_by_role(
            "textbox", name="选择维修负责人"
        )
        expect(mobile_handler_picker).to_be_visible()
        expect(
            page.locator(".account-picker-current", has_text="测试管理员")
        ).to_be_visible()
        page.get_by_role("button", name="取消").click()
        assert_no_page_overflow(page, "desktop repairs")
        page.screenshot(
            path=str(screenshot_dir / "repairs-desktop.png"),
            full_page=True,
        )

        page.evaluate(
            "() => localStorage.setItem('ca-admin-section-sidebar-collapsed', 'true')"
        )
        page.set_viewport_size({"width": 390, "height": 844})
        page.reload(wait_until="networkidle")
        page.get_by_role("button", name="新建维修").click()
        page.get_by_role("textbox", name="联系人", exact=True).fill("测试联系人")
        page.get_by_role("textbox", name="设备类型", exact=True).fill("测试电脑")
        page.get_by_role("textbox", name="故障描述", exact=True).fill("无法正常启动")
        page.get_by_role("button", name="下一步").click()
        expect(
            page.get_by_role("textbox", name="选择维修负责人")
        ).to_be_visible()
        modal_box = page.locator(".modal-shell").bounding_box()
        if (
            modal_box is None
            or modal_box["y"] >= 844
            or modal_box["y"] + modal_box["height"] <= 0
        ):
            raise AssertionError(
                f"mobile repair dialog is outside the viewport: {modal_box}"
            )
        print(
            "MOBILE_REPAIR_DIALOG_OK "
            f"y={modal_box['y']:.0f} height={modal_box['height']:.0f}"
        )
        mobile_handler_picker.scroll_into_view_if_needed()
        page.wait_for_timeout(400)
        assert_no_page_overflow(page, "mobile repairs")
        page.screenshot(
            path=str(screenshot_dir / "repairs-mobile.png")
        )
        browser.close()

    if console_errors:
        raise AssertionError(
            "browser console errors:\n" + "\n".join(console_errors)
        )
    print("UI_CORE_WORKFLOWS_OK pages=7 viewports=2")


if __name__ == "__main__":
    main()
