from pathlib import Path
from contextlib import closing
import sqlite3
import tempfile
import unittest

from performance_baseline import (
    SeedScale,
    benchmark_cases,
    browser_benchmark_cases,
    evaluate_performance,
    main,
    percentile,
    seed_database,
    summarize_samples,
)


class SeedScaleTest(unittest.TestCase):
    def test_reports_all_generated_business_rows(self):
        scale = SeedScale(
            users=500,
            attendance=10_000,
            trainings=500,
            training_participants_per_session=10,
            repairs=1_000,
            logs=5_000,
        )

        self.assertEqual(
            scale.expected_counts(),
            {
                "users": 500,
                "attendance_records": 10_000,
                "training_sessions": 500,
                "training_participants": 5_000,
                "repair_cases": 1_000,
                "operation_logs": 5_000,
            },
        )

    def test_rejects_non_positive_scale_values(self):
        with self.assertRaisesRegex(ValueError, "users"):
            SeedScale(users=0)

    def test_reports_special_training_and_repair_distributions(self):
        scale = SeedScale(
            users=80,
            attendance=100,
            trainings=200,
            training_participants_per_session=10,
            training_participant_counts=(0, 1, 30, 72, 3_000),
            repairs=1_100,
            repair_status_counts=(8, 1_050, 42),
            logs=100,
        )

        self.assertEqual(scale.expected_counts()["training_participants"], 5_053)
        self.assertEqual(scale.expected_counts()["repair_cases"], 1_100)

    def test_rejects_invalid_distribution_totals(self):
        with self.assertRaisesRegex(ValueError, "repair_status_counts"):
            SeedScale(repairs=10, repair_status_counts=(2, 3, 4))
        with self.assertRaisesRegex(ValueError, "training_participant_counts"):
            SeedScale(trainings=2, training_participant_counts=(0, 1, 2))

    def test_seeds_a_migrated_database_with_exact_counts_and_valid_foreign_keys(self):
        scale = SeedScale(
            users=8,
            attendance=20,
            trainings=5,
            training_participants_per_session=2,
            repairs=10,
            logs=15,
        )
        schema = (
            Path(__file__).resolve().parents[1] / "database" / "schema.sql"
        ).read_text(encoding="utf-8")

        with tempfile.TemporaryDirectory() as temporary:
            database = Path(temporary) / "attendance.db"
            with closing(sqlite3.connect(database)) as connection:
                connection.executescript(schema)
                connection.execute(
                    """
                    INSERT INTO users (
                      student_no, name, password_hash, role, status,
                      must_change_password
                    ) VALUES (?, ?, ?, 'ADMIN', 'ACTIVE', 0)
                    """,
                    ("9900000001", "性能测试管理员", "test-password-hash"),
                )
                connection.commit()

            result = seed_database(
                database,
                scale,
                random_seed=20260811,
                allowed_root=temporary,
            )

            self.assertEqual(result["counts"], scale.expected_counts())
            self.assertEqual(result["foreign_key_errors"], [])
            with closing(sqlite3.connect(database)) as connection:
                admin_hash = connection.execute(
                    "SELECT password_hash FROM users WHERE role = 'ADMIN'"
                ).fetchone()[0]
                seeded_hashes = {
                    row[0]
                    for row in connection.execute(
                        "SELECT password_hash FROM users WHERE role <> 'ADMIN'"
                    )
                }
            self.assertNotIn(admin_hash, seeded_hashes)

    def test_seeds_exact_special_distributions_and_unlinked_large_rosters(self):
        scale = SeedScale(
            users=8,
            attendance=20,
            trainings=5,
            training_participants_per_session=2,
            training_participant_counts=(0, 1, 3, 9, 15),
            repairs=7,
            repair_status_counts=(2, 4, 1),
            logs=15,
        )
        schema = (
            Path(__file__).resolve().parents[1] / "database" / "schema.sql"
        ).read_text(encoding="utf-8")

        with tempfile.TemporaryDirectory() as temporary:
            database = Path(temporary) / "attendance.db"
            with closing(sqlite3.connect(database)) as connection:
                connection.executescript(schema)
                connection.execute(
                    """
                    INSERT INTO users (
                      student_no, name, password_hash, role, status,
                      must_change_password
                    ) VALUES (?, ?, ?, 'ADMIN', 'ACTIVE', 0)
                    """,
                    ("9900000001", "性能测试管理员", "test-password-hash"),
                )
                connection.commit()

            result = seed_database(
                database,
                scale,
                random_seed=20260813,
                allowed_root=temporary,
            )

            with closing(sqlite3.connect(database)) as connection:
                roster_sizes = [
                    row[0]
                    for row in connection.execute(
                        """
                        SELECT COUNT(p.id)
                        FROM training_sessions s
                        LEFT JOIN training_participants p ON p.session_id = s.id
                        GROUP BY s.id
                        ORDER BY s.id
                        """
                    )
                ]
                repair_counts = dict(
                    connection.execute(
                        "SELECT status, COUNT(*) FROM repair_cases GROUP BY status"
                    )
                )
                unlinked = connection.execute(
                    "SELECT COUNT(*) FROM training_participants WHERE user_id IS NULL"
                ).fetchone()[0]

            self.assertEqual(roster_sizes, [0, 1, 3, 9, 15])
            self.assertEqual(
                repair_counts,
                {"REPAIRING": 2, "COMPLETED": 4, "CANCELED": 1},
            )
            self.assertGreater(unlinked, 0)
            self.assertEqual(result["foreign_key_errors"], [])

    def test_refuses_to_seed_a_database_outside_the_explicit_isolated_root(self):
        with tempfile.TemporaryDirectory() as allowed, tempfile.TemporaryDirectory() as other:
            database = Path(other) / "attendance.db"
            database.touch()

            with self.assertRaisesRegex(ValueError, "allowed root"):
                seed_database(
                    database,
                    SeedScale(users=2, attendance=2, trainings=1, repairs=1, logs=1),
                    allowed_root=allowed,
                )


class PercentileTest(unittest.TestCase):
    def test_uses_nearest_rank_for_reproducible_latency_results(self):
        samples = [40.0, 10.0, 30.0, 20.0]

        self.assertEqual(percentile(samples, 50), 20.0)
        self.assertEqual(percentile(samples, 95), 40.0)

    def test_summarizes_latency_and_response_size_without_losing_samples(self):
        result = summarize_samples([10.0, 20.0, 30.0], [100, 110, 120])

        self.assertEqual(result["samples"], 3)
        self.assertEqual(result["p50_ms"], 20.0)
        self.assertEqual(result["p95_ms"], 30.0)
        self.assertEqual(result["mean_bytes"], 110)


class BenchmarkCasesTest(unittest.TestCase):
    def test_covers_the_primary_large_data_workflows(self):
        cases = benchmark_cases("2025-08-12", "2026-08-11")
        names = {case.name for case in cases}

        self.assertTrue(
            {
                "members_page",
                "attendance_page",
                "stats_summary",
                "weekly_detail",
                "training_list",
                "repair_list",
                "logs_page",
                "custom_export_preview",
            }.issubset(names)
        )
        self.assertEqual(
            next(case.method for case in cases if case.name == "custom_export_preview"),
            "POST",
        )
        self.assertEqual(
            next(case.path for case in cases if case.name == "training_list"),
            "/api/trainings/page?page=1&pageSize=20",
        )
        self.assertEqual(
            next(case.path for case in cases if case.name == "repair_list"),
            "/api/repairs?status=REPAIRING&page=1&pageSize=20",
        )

    def test_routes_the_minister_password_only_to_functional_validation(self):
        script = (Path(__file__).parent / "run-performance-baseline.ps1").read_text(
            encoding="utf-8"
        )
        visual_block, validation_block = script.split(
            'large_dataset_visual.py")', 1
        )[1].split('large_dataset_validation.py")', 1)

        self.assertNotIn("--minister-password", visual_block)
        self.assertIn("--minister-password $MinisterPassword", validation_block)

    def test_browser_benchmarks_follow_current_training_and_repair_layouts(self):
        cases = {
            name: (route, selector)
            for name, route, selector in browser_benchmark_cases(
                "/#/admin/trainings?sessionId=5"
            )
        }

        self.assertEqual(cases["trainings"][1], ".training-ribbon-event")
        self.assertEqual(
            cases["training_large_roster"],
            ("/#/admin/trainings?sessionId=5", ".training-participant-row"),
        )
        self.assertEqual(cases["repairs"][1], ".repair-ledger-row")
        self.assertEqual(cases["repair_history"][1], ".repair-ledger-row")


class PerformanceGateTest(unittest.TestCase):
    def report(self, api_p95: float = 100.0) -> dict:
        return {
            "api": {
                "requests": {"members_page": {"p95_ms": api_p95}},
                "heavy_operations": {"stats_export": {"elapsed_ms": 1_000}},
            },
            "browser": {
                "pages": {
                    "members": {
                        "p95_ms": 500,
                        "max_dom_nodes": 1_200,
                        "max_heap_bytes": 64 * 1024 * 1024,
                    }
                }
            },
            "process": {
                "workingSetBytes": 500 * 1024 * 1024,
                "privateMemoryBytes": 600 * 1024 * 1024,
            },
        }

    def test_passes_when_absolute_budgets_are_met(self):
        result = evaluate_performance(self.report())

        self.assertTrue(result["passed"])
        self.assertEqual(result["violations"], [])

    def test_fails_for_absolute_or_baseline_regression(self):
        absolute = evaluate_performance(self.report(api_p95=250))
        regression = evaluate_performance(
            self.report(api_p95=180),
            self.report(api_p95=50),
        )

        self.assertFalse(absolute["passed"])
        self.assertFalse(regression["passed"])
        self.assertTrue(any("api.requests.members_page" in item for item in regression["violations"]))

    def test_evaluate_command_returns_nonzero_for_a_failed_gate(self):
        with tempfile.TemporaryDirectory() as temporary:
            report = Path(temporary) / "report.json"
            output = Path(temporary) / "gate.json"
            report.write_text(
                __import__("json").dumps(self.report(api_p95=250)),
                encoding="utf-8",
            )

            exit_code = main(
                ["evaluate", "--report", str(report), "--output", str(output)]
            )

            self.assertEqual(exit_code, 2)
            self.assertFalse(__import__("json").loads(output.read_text(encoding="utf-8"))["passed"])


if __name__ == "__main__":
    unittest.main()
