from io import BytesIO
import unittest
from zipfile import ZipFile

from large_dataset_validation import excel_column, simple_xlsx, xlsx_row_count


class WorkbookHelpersTest(unittest.TestCase):
    def test_builds_a_readable_xlsx_and_counts_physical_rows(self):
        workbook = simple_xlsx(
            [["学号", "姓名"], ["1001", "测试成员"], ["", "未关联成员"]]
        )

        self.assertEqual(xlsx_row_count(workbook), 3)
        with ZipFile(BytesIO(workbook)) as archive:
            self.assertIn("xl/workbook.xml", archive.namelist())

    def test_generates_excel_column_names(self):
        self.assertEqual(excel_column(0), "A")
        self.assertEqual(excel_column(25), "Z")
        self.assertEqual(excel_column(26), "AA")


if __name__ == "__main__":
    unittest.main()
