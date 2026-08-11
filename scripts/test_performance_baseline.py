from pathlib import Path
from contextlib import closing
import sqlite3
import tempfile
import unittest

from performance_baseline import (
    SeedScale,
    benchmark_cases,
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
                    ("1004231224", "性能测试管理员", "test-password-hash"),
                )
                connection.commit()

            result = seed_database(database, scale, random_seed=20260811)

            self.assertEqual(result["counts"], scale.expected_counts())
            self.assertEqual(result["foreign_key_errors"], [])


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


if __name__ == "__main__":
    unittest.main()
