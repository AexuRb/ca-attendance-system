#!/usr/bin/env python3
"""Generate isolated performance data and measure local API latency."""

from __future__ import annotations

from contextlib import closing
import argparse
from dataclasses import dataclass
from datetime import date, datetime, time, timedelta
import json
import math
from pathlib import Path
import random
import sqlite3
import statistics
import sys
from time import perf_counter
from typing import Any
from urllib.error import HTTPError
from urllib.request import Request, urlopen


@dataclass(frozen=True)
class SeedScale:
    users: int = 500
    attendance: int = 10_000
    trainings: int = 500
    training_participants_per_session: int = 10
    repairs: int = 1_000
    logs: int = 5_000

    def __post_init__(self) -> None:
        for name, value in self.__dict__.items():
            if value <= 0:
                raise ValueError(f"{name} must be greater than zero")

    def expected_counts(self) -> dict[str, int]:
        return {
            "users": self.users,
            "attendance_records": self.attendance,
            "training_sessions": self.trainings,
            "training_participants": (
                self.trainings * self.training_participants_per_session
            ),
            "repair_cases": self.repairs,
            "operation_logs": self.logs,
        }


@dataclass(frozen=True)
class BenchmarkCase:
    name: str
    method: str
    path: str
    body: dict[str, Any] | None = None
    iterations: int | None = None


def percentile(samples: list[float], rank: int) -> float:
    if not samples:
        raise ValueError("samples must not be empty")
    if rank < 1 or rank > 100:
        raise ValueError("rank must be between 1 and 100")
    ordered = sorted(samples)
    index = max(0, math.ceil(rank / 100 * len(ordered)) - 1)
    return ordered[index]


def summarize_samples(
    latencies_ms: list[float], response_sizes: list[int]
) -> dict[str, float | int]:
    if not latencies_ms or len(latencies_ms) != len(response_sizes):
        raise ValueError("latency and response size samples must be non-empty and aligned")
    return {
        "samples": len(latencies_ms),
        "min_ms": round(min(latencies_ms), 2),
        "p50_ms": round(percentile(latencies_ms, 50), 2),
        "p95_ms": round(percentile(latencies_ms, 95), 2),
        "max_ms": round(max(latencies_ms), 2),
        "mean_ms": round(statistics.fmean(latencies_ms), 2),
        "mean_bytes": round(statistics.fmean(response_sizes)),
        "max_bytes": max(response_sizes),
    }


def benchmark_cases(from_date: str, to_date: str) -> list[BenchmarkCase]:
    week_from = (date.fromisoformat(to_date) - timedelta(days=6)).isoformat()
    return [
        BenchmarkCase(
            "members_page",
            "GET",
            "/api/users/page?page=1&pageSize=50",
        ),
        BenchmarkCase(
            "attendance_page",
            "GET",
            (
                "/api/attendance/page?page=1&pageSize=50"
                f"&from={from_date}&to={to_date}"
            ),
        ),
        BenchmarkCase(
            "pending_reviews",
            "GET",
            "/api/attendance/reviews/pending",
        ),
        BenchmarkCase(
            "stats_summary",
            "GET",
            f"/api/stats/summary?from={from_date}&to={to_date}",
        ),
        BenchmarkCase(
            "weekly_detail",
            "GET",
            f"/api/stats/weekly-detail?from={week_from}&to={to_date}",
        ),
        BenchmarkCase(
            "training_list",
            "GET",
            "/api/trainings",
        ),
        BenchmarkCase(
            "repair_list",
            "GET",
            "/api/repairs",
        ),
        BenchmarkCase(
            "logs_page",
            "GET",
            "/api/logs?page=1&pageSize=50",
        ),
        BenchmarkCase(
            "maintenance_summary",
            "GET",
            "/api/maintenance/summary",
        ),
        BenchmarkCase(
            "custom_export_preview",
            "POST",
            "/api/exports/preview",
            {
                "source": "attendance",
                "fields": [
                    "dutyDate",
                    "studentNo",
                    "name",
                    "effectiveStatus",
                    "validHours",
                ],
                "filters": {"from": from_date, "to": to_date},
                "filename": "performance-preview",
            },
        ),
    ]


def benchmark_api(
    base_url: str,
    student_no: str,
    password: str,
    *,
    iterations: int = 12,
    warmups: int = 2,
    from_date: str | None = None,
    to_date: str | None = None,
) -> dict[str, Any]:
    if iterations <= 0 or warmups < 0:
        raise ValueError("iterations must be positive and warmups cannot be negative")
    to_value = date.fromisoformat(to_date) if to_date else date.today()
    from_value = (
        date.fromisoformat(from_date)
        if from_date
        else to_value - timedelta(days=364)
    )
    token = _login(base_url, student_no, password)
    results: dict[str, Any] = {}
    for case in benchmark_cases(from_value.isoformat(), to_value.isoformat()):
        for _ in range(warmups):
            _timed_request(base_url, case, token)
        latencies: list[float] = []
        response_sizes: list[int] = []
        for _ in range(case.iterations or iterations):
            elapsed, size = _timed_request(base_url, case, token)
            latencies.append(elapsed)
            response_sizes.append(size)
        results[case.name] = summarize_samples(latencies, response_sizes)

    heavy_results = {}
    export_body = next(
        case.body
        for case in benchmark_cases(from_value.isoformat(), to_value.isoformat())
        if case.name == "custom_export_preview"
    )
    for case in [
        BenchmarkCase(
            "stats_excel",
            "GET",
            f"/api/stats/export?from={from_value}&to={to_value}",
            iterations=1,
        ),
        BenchmarkCase(
            "custom_export_excel",
            "POST",
            "/api/exports/excel",
            export_body,
            iterations=1,
        ),
        BenchmarkCase(
            "database_backup",
            "POST",
            "/api/maintenance/backups",
            iterations=1,
        ),
    ]:
        elapsed, size = _timed_request(base_url, case, token)
        heavy_results[case.name] = {
            "elapsed_ms": round(elapsed, 2),
            "response_bytes": size,
        }
    return {
        "measured_at": datetime.now().isoformat(timespec="seconds"),
        "date_range": [from_value.isoformat(), to_value.isoformat()],
        "iterations": iterations,
        "warmups": warmups,
        "requests": results,
        "heavy_operations": heavy_results,
    }


def benchmark_browser(
    base_url: str,
    student_no: str,
    password: str,
    *,
    iterations: int = 3,
) -> dict[str, Any]:
    if iterations <= 0:
        raise ValueError("iterations must be positive")
    try:
        from playwright.sync_api import sync_playwright
    except ImportError as error:
        raise RuntimeError("Python Playwright is required for browser benchmarks") from error

    token = _login(base_url, student_no, password)
    cases = (
        ("members", "/#/admin/members", ".member-table tbody tr"),
        ("attendance", "/#/admin/attendance", ".table-shell tbody tr"),
        ("trainings", "/#/admin/trainings", ".record-list-item"),
        ("repairs", "/#/admin/repairs", ".repair-card"),
        ("logs", "/#/admin/logs", ".timeline-list article"),
    )
    results: dict[str, Any] = {}
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(
            headless=True,
            args=["--enable-precise-memory-info"],
        )
        context = browser.new_context(viewport={"width": 1440, "height": 968})
        context.add_init_script(
            """
            window.__caLongTasks = [];
            try {
              new PerformanceObserver((list) => {
                window.__caLongTasks.push(...list.getEntries().map((entry) => entry.duration));
              }).observe({type: 'longtask', buffered: true});
            } catch (_) {}
            """
        )
        context.add_init_script(
            f"localStorage.setItem('ca_attendance_token', {json.dumps(token)})"
        )
        for name, route, selector in cases:
            latencies: list[float] = []
            dom_nodes: list[int] = []
            rendered_items: list[int] = []
            heap_bytes: list[int] = []
            long_tasks: list[float] = []
            for iteration in range(iterations + 1):
                page = context.new_page()
                started = perf_counter()
                page.goto(base_url.rstrip("/") + route, wait_until="networkidle")
                page.locator(selector).first.wait_for(state="visible", timeout=30_000)
                elapsed = (perf_counter() - started) * 1000
                metrics = page.evaluate(
                    """
                    () => ({
                      domNodes: document.querySelectorAll('*').length,
                      heapBytes: performance.memory?.usedJSHeapSize || 0,
                      longTasks: window.__caLongTasks || []
                    })
                    """
                )
                item_count = page.locator(selector).count()
                if iteration > 0:
                    latencies.append(elapsed)
                    dom_nodes.append(metrics["domNodes"])
                    rendered_items.append(item_count)
                    heap_bytes.append(metrics["heapBytes"])
                    long_tasks.extend(metrics["longTasks"])
                page.close()
            summary = summarize_samples(latencies, dom_nodes)
            summary.update(
                {
                    "rendered_items": max(rendered_items),
                    "max_dom_nodes": max(dom_nodes),
                    "max_heap_bytes": max(heap_bytes),
                    "long_task_count": len(long_tasks),
                    "max_long_task_ms": round(max(long_tasks, default=0), 2),
                }
            )
            summary.pop("mean_bytes")
            summary.pop("max_bytes")
            results[name] = summary
        context.close()
        browser.close()
    return {
        "measured_at": datetime.now().isoformat(timespec="seconds"),
        "viewport": {"width": 1440, "height": 968},
        "iterations": iterations,
        "pages": results,
    }


def inspect_database(database: str | Path) -> dict[str, Any]:
    database_path = Path(database).resolve()
    with closing(sqlite3.connect(database_path, timeout=30)) as connection:
        counts = {
            table: connection.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
            for table in (
                "users",
                "attendance_records",
                "training_sessions",
                "training_participants",
                "repair_cases",
                "operation_logs",
            )
        }
        indexes = [
            row[0]
            for row in connection.execute(
                "SELECT name FROM sqlite_master WHERE type = 'index' ORDER BY name"
            )
            if not row[0].startswith("sqlite_autoindex")
        ]
        plans = {
            "attendance_page": _query_plan(
                connection,
                """
                SELECT id FROM attendance_records
                WHERE duty_date BETWEEN ? AND ?
                ORDER BY duty_date DESC, check_in_time DESC
                LIMIT 50
                """,
                ("2025-01-01", "2026-12-31"),
            ),
            "attendance_stats": _query_plan(
                connection,
                """
                SELECT user_id, SUM(valid_hours)
                FROM attendance_records
                WHERE effective_status = 'VALID' AND duty_date BETWEEN ? AND ?
                GROUP BY user_id
                """,
                ("2025-01-01", "2026-12-31"),
            ),
            "training_list": _query_plan(
                connection,
                """
                SELECT s.id,
                       (SELECT COUNT(*) FROM training_participants p WHERE p.session_id = s.id),
                       (SELECT COALESCE(SUM(p.duration_hours), 0) FROM training_participants p WHERE p.session_id = s.id)
                FROM training_sessions s
                ORDER BY s.training_date DESC, s.id DESC
                """,
            ),
            "repair_list": _query_plan(
                connection,
                """
                SELECT id FROM repair_cases
                WHERE deleted_at IS NULL
                ORDER BY CASE status WHEN 'REPAIRING' THEN 1 WHEN 'COMPLETED' THEN 2 ELSE 3 END,
                         received_at DESC, id DESC
                """,
            ),
            "logs_page": _query_plan(
                connection,
                """
                SELECT id FROM operation_logs
                ORDER BY created_at DESC, id DESC
                LIMIT 50
                """,
            ),
        }
        page_count = connection.execute("PRAGMA page_count").fetchone()[0]
        page_size = connection.execute("PRAGMA page_size").fetchone()[0]
        foreign_key_errors = [
            list(row) for row in connection.execute("PRAGMA foreign_key_check")
        ]
    return {
        "database": str(database_path),
        "database_bytes": page_count * page_size,
        "counts": counts,
        "indexes": indexes,
        "query_plans": plans,
        "foreign_key_errors": foreign_key_errors,
    }


def _query_plan(
    connection: sqlite3.Connection,
    sql: str,
    args: tuple[Any, ...] = (),
) -> list[str]:
    return [
        row[3]
        for row in connection.execute("EXPLAIN QUERY PLAN " + sql, args).fetchall()
    ]


def _login(base_url: str, student_no: str, password: str) -> str:
    _, payload = _request(
        base_url,
        "POST",
        "/api/auth/login",
        None,
        {"studentNo": student_no, "password": password},
    )
    value = json.loads(payload.decode("utf-8"))
    return value["token"]


def _timed_request(
    base_url: str, case: BenchmarkCase, token: str
) -> tuple[float, int]:
    started = perf_counter()
    _, payload = _request(base_url, case.method, case.path, token, case.body)
    return (perf_counter() - started) * 1000, len(payload)


def _request(
    base_url: str,
    method: str,
    path: str,
    token: str | None,
    body: dict[str, Any] | None,
) -> tuple[int, bytes]:
    headers = {"Accept": "application/json"}
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = Request(
        base_url.rstrip("/") + path,
        data=data,
        headers=headers,
        method=method,
    )
    try:
        with urlopen(request, timeout=120) as response:
            return response.status, response.read()
    except HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")[:500]
        raise RuntimeError(
            f"{method} {path} returned HTTP {error.code}: {detail}"
        ) from error


def _write_json(value: dict[str, Any], output: str | None) -> None:
    content = json.dumps(value, ensure_ascii=False, indent=2)
    if output:
        output_path = Path(output).resolve()
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(content + "\n", encoding="utf-8")
    else:
        print(content)


def seed_database(
    database: str | Path,
    scale: SeedScale,
    *,
    random_seed: int = 20260811,
) -> dict[str, Any]:
    database_path = Path(database).resolve()
    if not database_path.is_file():
        raise FileNotFoundError(f"database does not exist: {database_path}")

    started = perf_counter()
    rng = random.Random(random_seed)
    end_date = date.today()
    start_date = end_date - timedelta(days=1094)
    date_span = (end_date - start_date).days + 1

    with closing(sqlite3.connect(database_path, timeout=30)) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        connection.execute("PRAGMA busy_timeout = 30000")
        admin = connection.execute(
            """
            SELECT id, password_hash
            FROM users
            WHERE role = 'ADMIN' AND status = 'ACTIVE'
            ORDER BY id
            LIMIT 1
            """
        ).fetchone()
        if admin is None:
            raise ValueError("database must contain one active administrator")
        existing_users = connection.execute(
            "SELECT COUNT(*) FROM users"
        ).fetchone()[0]
        if existing_users != 1:
            raise ValueError(
                "performance seed requires a fresh database with exactly one user"
            )

        admin_id, password_hash = admin
        with connection:
            _seed_users(connection, scale, admin_id, password_hash)
            users = connection.execute(
                "SELECT id, student_no, name, role FROM users ORDER BY id"
            ).fetchall()
            _seed_attendance(
                connection,
                scale,
                users,
                admin_id,
                start_date,
                date_span,
                rng,
            )
            _seed_trainings(
                connection,
                scale,
                users,
                admin_id,
                start_date,
                date_span,
            )
            _seed_repairs(
                connection,
                scale,
                users,
                admin_id,
                start_date,
                date_span,
            )
            connection.execute("DELETE FROM operation_logs")
            _seed_logs(
                connection,
                scale,
                admin_id,
                start_date,
                date_span,
            )
            connection.execute("ANALYZE")

        counts = {
            table: connection.execute(
                f"SELECT COUNT(*) FROM {table}"
            ).fetchone()[0]
            for table in scale.expected_counts()
        }
        expected = scale.expected_counts()
        if counts != expected:
            raise RuntimeError(f"seed counts differ: expected={expected}, actual={counts}")
        foreign_key_errors = [
            list(row) for row in connection.execute("PRAGMA foreign_key_check")
        ]
        connection.execute("PRAGMA wal_checkpoint(TRUNCATE)")
        page_count = connection.execute("PRAGMA page_count").fetchone()[0]
        page_size = connection.execute("PRAGMA page_size").fetchone()[0]

    return {
        "database": str(database_path),
        "random_seed": random_seed,
        "date_range": [start_date.isoformat(), end_date.isoformat()],
        "counts": counts,
        "foreign_key_errors": foreign_key_errors,
        "database_bytes": page_count * page_size,
        "seed_seconds": round(perf_counter() - started, 3),
    }


def _seed_users(
    connection: sqlite3.Connection,
    scale: SeedScale,
    admin_id: int,
    password_hash: str,
) -> None:
    rows = []
    for index in range(1, scale.users):
        role = "PRESIDENT" if index <= 3 else "MINISTER" if index <= 23 else "MEMBER"
        rows.append(
            (
                str(9_000_000_000 + index),
                f"性能成员{index:04d}",
                password_hash,
                role,
                "DISABLED" if index % 53 == 0 else "ACTIVE",
                f"138{index:08d}"[-11:],
                f"学院{index % 12 + 1:02d}",
                str(2023 + index % 4),
                str(100_000_000 + index),
                admin_id,
                admin_id,
            )
        )
    connection.executemany(
        """
        INSERT INTO users (
          student_no, name, password_hash, role, status, phone, major, grade, qq,
          must_change_password, created_by, updated_by
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
        """,
        rows,
    )


def _seed_attendance(
    connection: sqlite3.Connection,
    scale: SeedScale,
    users: list[tuple[Any, ...]],
    admin_id: int,
    start_date: date,
    date_span: int,
    rng: random.Random,
) -> None:
    rows = []
    for index in range(scale.attendance):
        user_id, student_no, name, role = users[index % len(users)]
        duty_date = start_date + timedelta(days=(index * 17) % date_span)
        start_hour = 14 if index % 2 == 0 else 16
        checked_in = datetime.combine(duty_date, time(start_hour, index % 60))
        is_incomplete = index % 41 == 0
        is_rejected = not is_incomplete and index % 29 == 0
        checked_out = None if is_incomplete else checked_in + timedelta(hours=2)
        if is_incomplete:
            check_in_status = "PENDING"
            check_out_status = "NOT_SUBMITTED"
            effective_status = "INCOMPLETE"
            duration_minutes = valid_hours = 0
        elif is_rejected:
            check_in_status = "REJECTED"
            check_out_status = "APPROVED"
            effective_status = "INVALID"
            duration_minutes = valid_hours = 0
        else:
            check_in_status = "AUTO_APPROVED" if role == "MINISTER" else "APPROVED"
            check_out_status = check_in_status
            effective_status = "VALID"
            duration_minutes = 120
            valid_hours = 2
        rows.append(
            (
                user_id,
                student_no,
                name,
                duty_date.isoformat(),
                duty_date.isoweekday(),
                1 if duty_date.isoweekday() <= 5 else 0,
                0 if index % 37 == 0 else 1,
                checked_in.strftime("%Y-%m-%d %H:%M:%S"),
                checked_out.strftime("%Y-%m-%d %H:%M:%S") if checked_out else None,
                check_in_status,
                check_out_status,
                admin_id if not is_incomplete else None,
                admin_id if checked_out else None,
                "性能测试驳回" if is_rejected else None,
                duration_minutes,
                valid_hours,
                effective_status,
                "ADMIN_MANUAL" if rng.randrange(8) == 0 else "PUBLIC",
                "性能测试补录" if index % 8 == 0 else None,
                admin_id,
                admin_id,
            )
        )
    connection.executemany(
        """
        INSERT INTO attendance_records (
          user_id, student_no_snapshot, name_snapshot, duty_date, duty_weekday,
          is_duty_day, within_duty_period, require_duty_day, require_duty_period,
          check_in_time, check_out_time, check_in_status, check_out_status,
          check_in_reviewed_by, check_out_reviewed_by, check_in_reject_reason,
          duration_minutes, valid_hours, effective_status, source, manual_reason,
          created_by, updated_by
        ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        rows,
    )


def _seed_trainings(
    connection: sqlite3.Connection,
    scale: SeedScale,
    users: list[tuple[Any, ...]],
    admin_id: int,
    start_date: date,
    date_span: int,
) -> None:
    participant_rows = []
    for index in range(scale.trainings):
        training_date = start_date + timedelta(days=(index * 7) % date_span)
        cursor = connection.execute(
            """
            INSERT INTO training_sessions (
              title, training_date, start_time, end_time, location, speaker,
              description, status, created_by, updated_by
            ) VALUES (?, ?, '09:30', '11:30', ?, ?, ?, 'COMPLETED', ?, ?)
            """,
            (
                f"性能基线培训 {index + 1:04d}",
                training_date.isoformat(),
                f"培训室 {index % 8 + 1}",
                users[(index + 1) % len(users)][2],
                "用于验证大数据量下的培训列表、统计和导出性能。",
                admin_id,
                admin_id,
            ),
        )
        session_id = cursor.lastrowid
        for participant_index in range(scale.training_participants_per_session):
            user = users[(index * 11 + participant_index) % len(users)]
            participant_rows.append(
                (
                    session_id,
                    user[0],
                    user[1],
                    user[2],
                    2,
                    "主讲人" if participant_index == 0 else "参与培训",
                    "IMPORT" if participant_index else "MANUAL",
                    admin_id,
                    admin_id,
                )
            )
    connection.executemany(
        """
        INSERT INTO training_participants (
          session_id, user_id, student_no_snapshot, name_snapshot,
          attendance_status, duration_hours, remark, source,
          created_by, updated_by
        ) VALUES (?, ?, ?, ?, 'PRESENT', ?, ?, ?, ?, ?)
        """,
        participant_rows,
    )


def _seed_repairs(
    connection: sqlite3.Connection,
    scale: SeedScale,
    users: list[tuple[Any, ...]],
    admin_id: int,
    start_date: date,
    date_span: int,
) -> None:
    rows = []
    daily_sequence: dict[str, int] = {}
    for index in range(scale.repairs):
        received_date = start_date + timedelta(days=(index * 13) % date_span)
        date_key = received_date.strftime("%Y%m%d")
        daily_sequence[date_key] = daily_sequence.get(date_key, 0) + 1
        status = ("REPAIRING", "COMPLETED", "CANCELED")[index % 3]
        received_at = datetime.combine(received_date, time(10, index % 60))
        handler = users[(index * 5) % len(users)]
        rows.append(
            (
                f"JXWX{date_key}-{daily_sequence[date_key]:04d}",
                "PERSONAL_DEVICE" if index % 2 == 0 else "PUBLIC_DEVICE",
                f"送修人{index + 1:04d}",
                f"139{index:08d}"[-11:],
                "笔记本电脑" if index % 2 == 0 else "台式电脑",
                "Lenovo" if index % 3 == 0 else "Dell",
                f"性能测试型号 {index % 30 + 1}",
                "系统无法启动，偶发蓝屏并伴随存储设备读取异常。",
                "完成硬件检测、数据备份确认和系统修复。",
                status,
                received_at.strftime("%Y-%m-%d %H:%M:%S"),
                (
                    (received_at + timedelta(days=2)).strftime("%Y-%m-%d %H:%M:%S")
                    if status == "COMPLETED"
                    else None
                ),
                handler[0],
                handler[2],
                "性能测试维修事务",
                admin_id,
                admin_id,
            )
        )
    connection.executemany(
        """
        INSERT INTO repair_cases (
          case_no, agreement_type, owner_name, owner_phone, device_type,
          device_brand, device_model, fault_description, service_description,
          data_backup_confirmed, risk_acknowledged, privacy_acknowledged,
          status, received_at, completed_at, handler_user_id,
          handler_name_snapshot, remark, created_by, updated_by
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 1, 1, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        rows,
    )
    connection.execute("DELETE FROM repair_case_sequences")
    connection.executemany(
        """
        INSERT INTO repair_case_sequences (sequence_date, last_value)
        VALUES (?, ?)
        """,
        sorted(daily_sequence.items()),
    )


def _seed_logs(
    connection: sqlite3.Connection,
    scale: SeedScale,
    admin_id: int,
    start_date: date,
    date_span: int,
) -> None:
    actions = ("CREATE_USER", "UPDATE_ATTENDANCE", "CREATE_TRAINING", "UPDATE_REPAIR")
    rows = []
    for index in range(scale.logs):
        created = datetime.combine(
            start_date + timedelta(days=(index * 19) % date_span),
            time(index % 24, index % 60),
        )
        rows.append(
            (
                admin_id,
                "1004231224",
                "性能测试管理员",
                actions[index % len(actions)],
                "performance_seed",
                index + 1,
                json.dumps({"index": index}, ensure_ascii=False),
                json.dumps({"index": index + 1}, ensure_ascii=False),
                "性能基线数据",
                "127.0.0.1",
                "performance-baseline",
                created.strftime("%Y-%m-%d %H:%M:%S"),
            )
        )
    connection.executemany(
        """
        INSERT INTO operation_logs (
          operator_user_id, operator_student_no, operator_name, action_type,
          target_type, target_id, before_data, after_data, reason,
          ip_address, user_agent, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        rows,
    )


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Seed and benchmark an isolated CA attendance database."
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    seed_parser = subparsers.add_parser("seed", help="seed a migrated database")
    seed_parser.add_argument("--database", required=True)
    seed_parser.add_argument("--users", type=int, default=500)
    seed_parser.add_argument("--attendance", type=int, default=10_000)
    seed_parser.add_argument("--trainings", type=int, default=500)
    seed_parser.add_argument("--participants-per-training", type=int, default=10)
    seed_parser.add_argument("--repairs", type=int, default=1_000)
    seed_parser.add_argument("--logs", type=int, default=5_000)
    seed_parser.add_argument("--random-seed", type=int, default=20260811)
    seed_parser.add_argument("--output")

    benchmark_parser = subparsers.add_parser(
        "benchmark", help="measure authenticated local API endpoints"
    )
    benchmark_parser.add_argument("--base-url", required=True)
    benchmark_parser.add_argument("--student-no", default="1004231224")
    benchmark_parser.add_argument("--password", default="123456")
    benchmark_parser.add_argument("--iterations", type=int, default=12)
    benchmark_parser.add_argument("--warmups", type=int, default=2)
    benchmark_parser.add_argument("--from-date")
    benchmark_parser.add_argument("--to-date")
    benchmark_parser.add_argument("--output")

    browser_parser = subparsers.add_parser(
        "browser", help="measure large-list rendering in Chromium"
    )
    browser_parser.add_argument("--base-url", required=True)
    browser_parser.add_argument("--student-no", default="1004231224")
    browser_parser.add_argument("--password", default="123456")
    browser_parser.add_argument("--iterations", type=int, default=3)
    browser_parser.add_argument("--output")

    inspect_parser = subparsers.add_parser(
        "inspect", help="capture SQLite counts, indexes and query plans"
    )
    inspect_parser.add_argument("--database", required=True)
    inspect_parser.add_argument("--output")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    if args.command == "seed":
        result = seed_database(
            args.database,
            SeedScale(
                users=args.users,
                attendance=args.attendance,
                trainings=args.trainings,
                training_participants_per_session=args.participants_per_training,
                repairs=args.repairs,
                logs=args.logs,
            ),
            random_seed=args.random_seed,
        )
    elif args.command == "benchmark":
        result = benchmark_api(
            args.base_url,
            args.student_no,
            args.password,
            iterations=args.iterations,
            warmups=args.warmups,
            from_date=args.from_date,
            to_date=args.to_date,
        )
    elif args.command == "browser":
        result = benchmark_browser(
            args.base_url,
            args.student_no,
            args.password,
            iterations=args.iterations,
        )
    else:
        result = inspect_database(args.database)
    _write_json(result, args.output)
    return 0


if __name__ == "__main__":
    sys.exit(main())
