import argparse
import os
import re
import sys
from pathlib import Path

import bcrypt
import mysql.connector
from openpyxl import load_workbook


DEFAULT_EXCEL = r"C:\Users\AexuRb\Desktop\杂物\课程资料\2025-2026学年计算机协会社团成员信息表.xlsx"


def clean(value):
    if value is None:
        return ""
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    return str(value).strip()


def normalize_grade(value):
    text = clean(value)
    match = re.search(r"(\d{4})\s*级", text)
    if match:
        return f"{match.group(1)}级"
    return text


def parse_members(path):
    wb = load_workbook(path, data_only=True, read_only=True)
    ws = wb.active
    members = []
    skipped = []
    seen = set()
    duplicates = []

    for row_no, row in enumerate(ws.iter_rows(min_row=3, values_only=True), start=3):
        name = clean(row[1] if len(row) > 1 else "")
        college = clean(row[3] if len(row) > 3 else "")
        grade = normalize_grade(row[4] if len(row) > 4 else "")
        student_no = clean(row[5] if len(row) > 5 else "")
        phone = clean(row[8] if len(row) > 8 else "")

        if not any([name, student_no, phone, college, grade]):
            continue
        if not name or not student_no:
            skipped.append((row_no, "missing_name_or_student_no"))
            continue
        if not re.fullmatch(r"\d{6,32}", student_no):
            skipped.append((row_no, f"invalid_student_no:{student_no}"))
            continue
        if student_no in seen:
            duplicates.append((row_no, student_no))
            continue
        seen.add(student_no)

        members.append(
            {
                "student_no": student_no,
                "name": name,
                "phone": phone or None,
                "major": college or None,
                "grade": grade or None,
                "qq": None,
            }
        )

    return members, skipped, duplicates


def make_password_hash(student_no):
    initial_password = student_no[-6:]
    return bcrypt.hashpw(initial_password.encode("utf-8"), bcrypt.gensalt(rounds=10)).decode("utf-8")


def connect(args):
    password = args.password or os.environ.get("MYSQL_PWD") or os.environ.get("DB_PASSWORD")
    if not password:
        raise RuntimeError("Missing MySQL password. Pass --password or set MYSQL_PWD/DB_PASSWORD.")
    return mysql.connector.connect(
        host=args.host,
        port=args.port,
        user=args.user,
        password=password,
        database=args.database,
        charset="utf8mb4",
        use_unicode=True,
    )


def import_members(args, members):
    sql = """
        INSERT INTO users (
          student_no, name, password_hash, role, status, phone, major, grade, qq, must_change_password
        ) VALUES (
          %(student_no)s, %(name)s, %(password_hash)s, 'MEMBER', 'ACTIVE',
          %(phone)s, %(major)s, %(grade)s, %(qq)s, 1
        )
        ON DUPLICATE KEY UPDATE
          name = VALUES(name),
          phone = VALUES(phone),
          major = VALUES(major),
          grade = VALUES(grade),
          qq = COALESCE(users.qq, VALUES(qq)),
          updated_at = CURRENT_TIMESTAMP
    """
    payload = []
    for member in members:
        item = dict(member)
        item["password_hash"] = make_password_hash(member["student_no"])
        payload.append(item)

    conn = connect(args)
    try:
        cur = conn.cursor()
        cur.executemany(sql, payload)
        conn.commit()
        cur.close()
    finally:
        conn.close()


def main():
    parser = argparse.ArgumentParser(description="Import association members from Excel into MySQL.")
    parser.add_argument("--excel", default=DEFAULT_EXCEL)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=3306)
    parser.add_argument("--user", default="root")
    parser.add_argument("--password", default=None)
    parser.add_argument("--database", default="ca_attendance")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    path = Path(args.excel)
    if not path.exists():
        print(f"Excel file not found: {path}", file=sys.stderr)
        return 1

    members, skipped, duplicates = parse_members(path)
    print(f"valid_members={len(members)}")
    print(f"skipped_rows={len(skipped)}")
    print(f"duplicate_rows={len(duplicates)}")
    if skipped[:10]:
        print("skipped_sample=" + repr(skipped[:10]))
    if duplicates[:10]:
        print("duplicate_sample=" + repr(duplicates[:10]))

    if args.dry_run:
        return 0

    import_members(args, members)
    print(f"imported_or_updated={len(members)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
