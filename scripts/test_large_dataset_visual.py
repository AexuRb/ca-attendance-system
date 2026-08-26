import unittest

from large_dataset_visual import (
    REPAIR_ROW_SELECTOR,
    TRAINING_ROW_SELECTOR,
    VIEWPORTS,
    validate_metrics,
)


class VisualValidationTest(unittest.TestCase):
    def test_covers_all_supported_viewports(self):
        self.assertEqual(
            [(width, height) for _, width, height in VIEWPORTS],
            [(1440, 900), (1024, 768), (768, 1024), (390, 844)],
        )

    def test_uses_current_paginated_training_and_repair_rows(self):
        self.assertEqual(TRAINING_ROW_SELECTOR, ".training-participant-row")
        self.assertEqual(REPAIR_ROW_SELECTOR, ".repair-ledger-row")

    def test_accepts_a_paginated_page_without_overflow(self):
        validate_metrics(
            {
                "horizontalOverflow": 0,
                "renderedRows": 30,
                "domNodes": 1_200,
                "overlappingCells": 0,
                "documentHeight": 4_000,
                "viewportHeight": 844,
            },
            row_limit=30,
        )

    def test_rejects_unbounded_content(self):
        with self.assertRaisesRegex(AssertionError, "rendered"):
            validate_metrics(
                {
                    "horizontalOverflow": 0,
                    "renderedRows": 3_000,
                    "domNodes": 20_000,
                    "overlappingCells": 0,
                    "documentHeight": 100_000,
                    "viewportHeight": 900,
                },
                row_limit=30,
            )


if __name__ == "__main__":
    unittest.main()
