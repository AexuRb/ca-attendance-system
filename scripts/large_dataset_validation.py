#!/usr/bin/env python3
"""Validate paginated training and repair workflows against an isolated large database."""

from __future__ import annotations

import argparse
from datetime import date
from html import escape
from io import BytesIO
import json
from pathlib import Path
from time import perf_counter
from typing import Any
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from uuid import uuid4
from zipfile import ZIP_DEFLATED, ZipFile
import xml.etree.ElementTree as ET


def validate_large_dataset(
    base_url: str,
    student_no: str,
    password: str,
    from_date: str,
    to_date: str,
) -> dict[str, Any]:
    base_url = base_url.rstrip("/")
    token, admin = login(base_url, student_no, password)
    checks: list[dict[str, Any]] = []

    def check(name: str, condition: bool, detail: Any) -> None:
        if not condition:
            raise AssertionError(f"{name}: {detail}")
        checks.append({"name": name, "detail": detail})

    training_query = urlencode(
        {"from": from_date, "to": to_date, "page": 1, "pageSize": 20}
    )
    training_page = request_json(
        base_url, "GET", f"/api/trainings/page?{training_query}", token
    )
    check(
        "training_session_page",
        training_page["total"] == 200
        and len(training_page["items"]) == 20
        and training_page["hasMore"],
        {"total": training_page["total"], "rendered": len(training_page["items"])},
    )

    last_training_page = request_json(
        base_url,
        "GET",
        f"/api/trainings/page?{urlencode({'from': from_date, 'to': to_date, 'page': 10, 'pageSize': 20})}",
        token,
    )
    check(
        "training_session_last_page",
        len(last_training_page["items"]) == 20
        and not last_training_page["hasMore"],
        {"page": last_training_page["page"], "rendered": len(last_training_page["items"])},
    )

    large_training = request_json(
        base_url,
        "GET",
        "/api/trainings/page?keyword="
        + urlencode({"value": "超长标题测试"}).split("=", 1)[1]
        + "&page=1&pageSize=20&from="
        + from_date
        + "&to="
        + to_date,
        token,
    )["items"][0]
    large_id = large_training["id"]
    first_roster = request_json(
        base_url,
        "GET",
        f"/api/trainings/{large_id}/participants/page?page=1&pageSize=30",
        token,
    )
    last_roster = request_json(
        base_url,
        "GET",
        f"/api/trainings/{large_id}/participants/page?page=100&pageSize=30",
        token,
    )
    roster_search = request_json(
        base_url,
        "GET",
        f"/api/trainings/{large_id}/participants/page?keyword="
        + urlencode({"value": "未关联参与成员2999"}).split("=", 1)[1]
        + "&page=1&pageSize=30",
        token,
    )
    check(
        "training_large_roster_pagination",
        first_roster["total"] == 3_000
        and len(first_roster["items"]) == 30
        and len(last_roster["items"]) == 30
        and not last_roster["hasMore"],
        {
            "total": first_roster["total"],
            "firstRendered": len(first_roster["items"]),
            "lastRendered": len(last_roster["items"]),
        },
    )
    check(
        "training_large_roster_search",
        roster_search["total"] == 1
        and roster_search["items"][0]["userId"] is None,
        {"total": roster_search["total"], "name": roster_search["items"][0]["name"]},
    )

    training_export_started = perf_counter()
    training_export = request_bytes(
        base_url, "GET", f"/api/trainings/{large_id}/export", token
    )
    training_export_ms = elapsed_ms(training_export_started)
    check(
        "training_large_roster_export",
        xlsx_row_count(training_export) == 3_003,
        {"rows": 3_003, "bytes": len(training_export), "elapsedMs": training_export_ms},
    )

    summary_export = request_bytes(
        base_url,
        "GET",
        f"/api/trainings/export?from={from_date}&to={to_date}",
        token,
    )
    check(
        "training_summary_export",
        xlsx_row_count(summary_export) == 202,
        {"sessionSheetRows": 202, "bytes": len(summary_export)},
    )

    repair_pages: dict[str, Any] = {}
    expected_repairs = {"REPAIRING": 8, "COMPLETED": 1_050, "CANCELED": 42}
    for status, total in expected_repairs.items():
        query = urlencode(
            {
                "status": status,
                "from": from_date,
                "to": to_date,
                "page": 1,
                "pageSize": 30,
            }
        )
        page = request_json(base_url, "GET", f"/api/repairs?{query}", token)
        repair_pages[status] = page
        check(
            f"repair_{status.lower()}_page",
            page["total"] == total and len(page["items"]) == min(total, 30),
            {"total": page["total"], "rendered": len(page["items"])},
        )
    completed_last = request_json(
        base_url,
        "GET",
        f"/api/repairs?{urlencode({'status': 'COMPLETED', 'from': from_date, 'to': to_date, 'page': 35, 'pageSize': 30})}",
        token,
    )
    check(
        "repair_completed_last_page",
        len(completed_last["items"]) == 30 and not completed_last["hasMore"],
        {"page": completed_last["page"], "rendered": len(completed_last["items"])},
    )

    repair_export_started = perf_counter()
    repair_export = request_bytes(
        base_url,
        "GET",
        f"/api/repairs/export?status=ALL&from={from_date}&to={to_date}",
        token,
    )
    repair_export_ms = elapsed_ms(repair_export_started)
    check(
        "repair_complete_export",
        xlsx_row_count(repair_export) == 1_102,
        {"rows": 1_102, "bytes": len(repair_export), "elapsedMs": repair_export_ms},
    )

    repair = repair_pages["REPAIRING"]["items"][0]
    agreement = request_bytes(
        base_url, "GET", f"/api/repairs/{repair['id']}/agreement", token
    ).decode("utf-8")
    check(
        "repair_agreement_preview",
        repair["caseNo"] in agreement and "协议" in agreement,
        {"caseNo": repair["caseNo"], "bytes": len(agreement.encode("utf-8"))},
    )

    created_session = request_json(
        base_url,
        "POST",
        "/api/trainings",
        token,
        {
            "title": "大数据验收临时培训",
            "trainingDate": date.today().isoformat(),
            "startTime": "14:00",
            "endTime": "16:00",
            "location": "验收教室",
            "speaker": "验收主讲人",
            "description": "用于验证大数据数据库中的 CRUD 与导入流程",
            "status": "COMPLETED",
        },
    )
    session_id = created_session["id"]
    created_participant = request_json(
        base_url,
        "POST",
        f"/api/trainings/{session_id}/participants",
        token,
        {
            "studentNo": "779900000001",
            "name": "临时参与人",
            "durationHours": 2,
            "remark": "新增",
        },
    )
    updated_participant = request_json(
        base_url,
        "PUT",
        f"/api/trainings/{session_id}/participants/{created_participant['id']}",
        token,
        {
            "studentNo": "779900000001",
            "name": "临时参与人",
            "durationHours": 1.5,
            "remark": "已更新",
        },
    )
    request_bytes(
        base_url,
        "DELETE",
        f"/api/trainings/{session_id}/participants/{created_participant['id']}",
        token,
    )
    import_result = upload_xlsx(
        base_url,
        f"/api/trainings/{session_id}/participants/import",
        token,
        simple_xlsx(
            [
                ["学号", "姓名", "时长", "备注"],
                ["779900000002", "导入主讲人", "2", "主讲人"],
                ["779900000003", "导入参与人", "1.5", "批量导入"],
            ]
        ),
    )
    updated_session = request_json(
        base_url,
        "PUT",
        f"/api/trainings/{session_id}",
        token,
        {**created_session, "title": "大数据验收临时培训（已更新）"},
    )
    request_bytes(base_url, "DELETE", f"/api/trainings/{session_id}", token)
    check(
        "training_crud_and_import",
        updated_participant["durationHours"] == 1.5
        and import_result["created"] == 2
        and updated_session["title"].endswith("（已更新）"),
        {"sessionId": session_id, "imported": import_result["created"]},
    )

    created_repair = request_json(
        base_url,
        "POST",
        "/api/repairs",
        token,
        {
            "agreementType": "REPAIR",
            "ownerName": "大数据验收送修人",
            "ownerPhone": "13800000000",
            "deviceType": "笔记本电脑",
            "deviceBrand": "测试品牌",
            "deviceModel": "TEST-22",
            "faultDescription": "用于验证大数据数据库中的维修 CRUD",
            "serviceDescription": "初步检测",
            "dataBackupConfirmed": True,
            "riskAcknowledged": True,
            "privacyAcknowledged": True,
            "status": "REPAIRING",
            "receivedAt": date.today().isoformat() + "T14:00:00",
            "handlerUserId": admin["id"],
            "handlerName": admin["name"],
            "remark": "验收临时事务",
        },
    )
    updated_repair = request_json(
        base_url,
        "PUT",
        f"/api/repairs/{created_repair['id']}",
        token,
        {**created_repair, "serviceDescription": "已完成二次检测"},
    )
    deleted_repair = request_json(
        base_url, "DELETE", f"/api/repairs/{created_repair['id']}", token
    )
    restored_repair = request_json(
        base_url,
        "POST",
        f"/api/repairs/{created_repair['id']}/restore",
        token,
        {},
    )
    check(
        "repair_crud_and_restore",
        updated_repair["serviceDescription"] == "已完成二次检测"
        and deleted_repair["deletedAt"] is not None
        and restored_repair["deletedAt"] is None,
        {"caseNo": created_repair["caseNo"]},
    )
    request_json(
        base_url, "DELETE", f"/api/repairs/{created_repair['id']}", token
    )

    minister_token, _ = login(base_url, "9000000004", password)
    training_status = request_status(
        base_url, "GET", f"/api/trainings/page?{training_query}", minister_token
    )
    repair_export_status = request_status(
        base_url,
        "GET",
        f"/api/repairs/export?status=ALL&from={from_date}&to={to_date}",
        minister_token,
    )
    check(
        "minister_permission_boundary",
        training_status == 403 and repair_export_status == 403,
        {"training": training_status, "repairExport": repair_export_status},
    )

    return {
        "validatedAt": date.today().isoformat(),
        "checks": checks,
        "largeTrainingId": large_id,
        "largeTrainingTitle": large_training["title"],
    }


def login(base_url: str, student_no: str, password: str) -> tuple[str, dict[str, Any]]:
    payload = request_json(
        base_url,
        "POST",
        "/api/auth/login",
        None,
        {"studentNo": student_no, "password": password},
    )
    return payload["token"], payload


def request_json(
    base_url: str,
    method: str,
    path: str,
    token: str | None,
    body: dict[str, Any] | None = None,
) -> Any:
    return json.loads(request_bytes(base_url, method, path, token, body).decode("utf-8"))


def request_bytes(
    base_url: str,
    method: str,
    path: str,
    token: str | None,
    body: dict[str, Any] | None = None,
    *,
    content_type: str | None = None,
    raw_body: bytes | None = None,
) -> bytes:
    headers = {"Accept": "application/json"}
    data = raw_body
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    if content_type:
        headers["Content-Type"] = content_type
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = Request(base_url + path, data=data, headers=headers, method=method)
    try:
        with urlopen(request, timeout=180) as response:
            return response.read()
    except HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")[:500]
        raise RuntimeError(f"{method} {path} returned HTTP {error.code}: {detail}") from error


def request_status(
    base_url: str, method: str, path: str, token: str | None
) -> int:
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    try:
        with urlopen(Request(base_url + path, headers=headers, method=method), timeout=120) as response:
            return response.status
    except HTTPError as error:
        return error.code


def upload_xlsx(base_url: str, path: str, token: str, workbook: bytes) -> Any:
    boundary = "----ca-large-data-" + uuid4().hex
    body = (
        f"--{boundary}\r\n"
        'Content-Disposition: form-data; name="file"; filename="large-data.xlsx"\r\n'
        "Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n"
    ).encode("ascii") + workbook + f"\r\n--{boundary}--\r\n".encode("ascii")
    return json.loads(
        request_bytes(
            base_url,
            "POST",
            path,
            token,
            content_type=f"multipart/form-data; boundary={boundary}",
            raw_body=body,
        ).decode("utf-8")
    )


def simple_xlsx(rows: list[list[str]]) -> bytes:
    row_xml = []
    for row_index, row in enumerate(rows, start=1):
        cells = []
        for column_index, value in enumerate(row):
            ref = f"{excel_column(column_index)}{row_index}"
            cells.append(
                f'<c r="{ref}" t="inlineStr"><is><t>{escape(str(value))}</t></is></c>'
            )
        row_xml.append(f'<row r="{row_index}">{"".join(cells)}</row>')
    output = BytesIO()
    with ZipFile(output, "w", ZIP_DEFLATED) as archive:
        archive.writestr(
            "[Content_Types].xml",
            '<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>',
        )
        archive.writestr(
            "_rels/.rels",
            '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>',
        )
        archive.writestr(
            "xl/workbook.xml",
            '<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="参与名单" sheetId="1" r:id="rId1"/></sheets></workbook>',
        )
        archive.writestr(
            "xl/_rels/workbook.xml.rels",
            '<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>',
        )
        archive.writestr(
            "xl/worksheets/sheet1.xml",
            '<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>'
            + "".join(row_xml)
            + "</sheetData></worksheet>",
        )
    return output.getvalue()


def xlsx_row_count(content: bytes, sheet: str = "xl/worksheets/sheet1.xml") -> int:
    with ZipFile(BytesIO(content)) as archive:
        root = ET.fromstring(archive.read(sheet))
    return len(root.findall(".//{http://schemas.openxmlformats.org/spreadsheetml/2006/main}row"))


def excel_column(index: int) -> str:
    value = index + 1
    result = ""
    while value:
        value, remainder = divmod(value - 1, 26)
        result = chr(65 + remainder) + result
    return result


def elapsed_ms(started: float) -> float:
    return round((perf_counter() - started) * 1_000, 2)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--student-no", default="1004231224")
    parser.add_argument("--password", default="123456")
    parser.add_argument("--from-date", required=True)
    parser.add_argument("--to-date", required=True)
    parser.add_argument("--output")
    args = parser.parse_args()
    result = validate_large_dataset(
        args.base_url,
        args.student_no,
        args.password,
        args.from_date,
        args.to_date,
    )
    content = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        output = Path(args.output).resolve()
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(content, encoding="utf-8")
    else:
        print(content, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
