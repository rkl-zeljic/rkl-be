"""Unit tests for Miki RKL application."""

from __future__ import annotations

import os
import sqlite3
import tempfile
from datetime import datetime
from typing import Generator

import pandas as pd
import pytest

# Add parent directory to path for imports
import sys
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Updated imports for refactored code
from utils.text import normalize_whitespace
from utils.dates import extract_report_date_from_filename
from utils.excel import find_header_row
from services.database import ensure_sqlite_schema, upsert_rows, _validate_table_name
from services.transformation import df_to_db_rows
from core.constants import DB_COLUMNS


class TestNormalizeWhitespace:
    """Tests for normalize_whitespace function."""

    def test_single_spaces(self) -> None:
        assert normalize_whitespace("hello world") == "hello world"

    def test_multiple_spaces(self) -> None:
        assert normalize_whitespace("hello    world") == "hello world"

    def test_tabs_and_newlines(self) -> None:
        assert normalize_whitespace("hello\t\nworld") == "hello world"

    def test_leading_trailing(self) -> None:
        assert normalize_whitespace("  hello world  ") == "hello world"

    def test_empty_string(self) -> None:
        assert normalize_whitespace("") == ""

    def test_only_whitespace(self) -> None:
        assert normalize_whitespace("   \t\n   ") == ""


class TestExtractReportDateFromFilename:
    """Tests for extract_report_date_from_filename function."""

    def test_valid_date(self) -> None:
        result = extract_report_date_from_filename("25.12.2025. Rudnik kamena Likodra.xls")
        assert result == "2025-12-25"

    def test_date_in_path(self) -> None:
        result = extract_report_date_from_filename("/path/to/01.01.2026. Report.xlsx")
        assert result == "2026-01-01"

    def test_no_date(self) -> None:
        result = extract_report_date_from_filename("report.xls")
        assert result is None

    def test_invalid_date(self) -> None:
        result = extract_report_date_from_filename("32.13.2025. Invalid.xls")
        assert result is None

    def test_partial_date(self) -> None:
        result = extract_report_date_from_filename("25.12. Incomplete.xls")
        assert result is None


class TestValidateTableName:
    """Tests for _validate_table_name function."""

    def test_valid_names(self) -> None:
        assert _validate_table_name("merenja") == "merenja"
        assert _validate_table_name("measurements") == "measurements"
        assert _validate_table_name("table_123") == "table_123"
        assert _validate_table_name("_private") == "_private"
        assert _validate_table_name("TableName") == "TableName"

    def test_invalid_names(self) -> None:
        with pytest.raises(ValueError):
            _validate_table_name("123table")  # starts with number

        with pytest.raises(ValueError):
            _validate_table_name("table-name")  # contains hyphen

        with pytest.raises(ValueError):
            _validate_table_name("table name")  # contains space

        with pytest.raises(ValueError):
            _validate_table_name("table;DROP")  # SQL injection attempt

        with pytest.raises(ValueError):
            _validate_table_name("")  # empty


class TestFindHeaderRow:
    """Tests for find_header_row function."""

    def test_header_in_first_row(self) -> None:
        df = pd.DataFrame([["Merni list br", "Bruto", "Tara"]])
        assert find_header_row(df) == 0

    def test_header_in_third_row(self) -> None:
        df = pd.DataFrame([
            ["Title", "", ""],
            ["Subtitle", "", ""],
            ["Merni list br", "Bruto", "Tara"],
            [1, 100, 50],
        ])
        assert find_header_row(df) == 2

    def test_header_case_insensitive(self) -> None:
        df = pd.DataFrame([["MERNI LIST BR", "Bruto"]])
        assert find_header_row(df) == 0

    def test_header_not_found(self) -> None:
        df = pd.DataFrame([["Column1", "Column2"]])
        with pytest.raises(ValueError, match="Header row.*not found"):
            find_header_row(df)


@pytest.fixture
def temp_db() -> Generator[str, None, None]:
    """Create a temporary database for testing."""
    with tempfile.NamedTemporaryFile(suffix=".db", delete=False) as f:
        db_path = f.name
    yield db_path
    os.unlink(db_path)


class TestEnsureSqliteSchema:
    """Tests for ensure_sqlite_schema function."""

    def test_creates_table(self, temp_db: str) -> None:
        conn = sqlite3.connect(temp_db)
        try:
            ensure_sqlite_schema(conn, "test_table")
            cursor = conn.execute(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='test_table'"
            )
            assert cursor.fetchone() is not None
        finally:
            conn.close()

    def test_creates_indices(self, temp_db: str) -> None:
        conn = sqlite3.connect(temp_db)
        try:
            ensure_sqlite_schema(conn, "test_table")
            cursor = conn.execute(
                "SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'ix_test_table%'"
            )
            indices = cursor.fetchall()
            assert len(indices) >= 2  # roba, registracija, datum
        finally:
            conn.close()

    def test_idempotent(self, temp_db: str) -> None:
        conn = sqlite3.connect(temp_db)
        try:
            ensure_sqlite_schema(conn, "test_table")
            ensure_sqlite_schema(conn, "test_table")  # Should not raise
        finally:
            conn.close()


class TestDfToDbRows:
    """Tests for df_to_db_rows function."""

    def test_basic_conversion(self) -> None:
        df = pd.DataFrame({
            "izvor_fajl": ["test.xls"],
            "datum_izvestaja": ["2025-12-25"],
            "Merni list br": [1],
            "Pošiljalac": ["Sender"],
            "Poručilac": ["Requestor"],
            "Primalac": ["Receiver"],
            "Roba": ["Agregat"],
            "Bruto": [1000.0],
            "Tara": [500.0],
            "Neto": [500.0],
            "Prevoznik": ["Transporter"],
            "Registracija": ["AB-123"],
            "Prikolica": [""],
            "Vozač": ["Driver"],
            "Broj lične karte": ["123456"],
        })
        rows = df_to_db_rows(df)
        assert len(rows) == 1
        assert rows[0]["merni_list_br"] == 1
        assert rows[0]["roba"] == "Agregat"
        assert rows[0]["neto"] == 500.0

    def test_null_handling(self) -> None:
        df = pd.DataFrame({
            "izvor_fajl": ["test.xls"],
            "datum_izvestaja": [None],
            "Merni list br": [1],
        })
        rows = df_to_db_rows(df)
        assert rows[0]["datum_izvestaja"] is None


class TestUpsertRows:
    """Tests for upsert_rows function."""

    def test_insert_new_rows(self, temp_db: str) -> None:
        conn = sqlite3.connect(temp_db)
        try:
            ensure_sqlite_schema(conn, "merenja")
            rows = [
                {c: f"value_{c}" if c not in ("bruto", "tara", "neto", "merni_list_br") else 1
                 for c in DB_COLUMNS}
            ]
            rows[0]["merni_list_br"] = 1
            rows[0]["datum_izvestaja"] = "2025-12-25"
            processed, _ = upsert_rows(conn, "merenja", rows)
            assert processed == 1

            cursor = conn.execute("SELECT COUNT(*) FROM merenja")
            assert cursor.fetchone()[0] == 1
        finally:
            conn.close()

    def test_upsert_updates_existing(self, temp_db: str) -> None:
        conn = sqlite3.connect(temp_db)
        try:
            ensure_sqlite_schema(conn, "merenja")

            # First insert
            rows = [{c: None for c in DB_COLUMNS}]
            rows[0]["merni_list_br"] = 1
            rows[0]["datum_izvestaja"] = "2025-12-25"
            rows[0]["roba"] = "Original"
            upsert_rows(conn, "merenja", rows)

            # Update with same key
            rows[0]["roba"] = "Updated"
            upsert_rows(conn, "merenja", rows)

            cursor = conn.execute("SELECT COUNT(*) FROM merenja")
            assert cursor.fetchone()[0] == 1

            cursor = conn.execute("SELECT roba FROM merenja WHERE merni_list_br = 1")
            assert cursor.fetchone()[0] == "Updated"
        finally:
            conn.close()

    def test_empty_rows(self, temp_db: str) -> None:
        conn = sqlite3.connect(temp_db)
        try:
            ensure_sqlite_schema(conn, "merenja")
            processed, _ = upsert_rows(conn, "merenja", [])
            assert processed == 0
        finally:
            conn.close()


class TestIntegration:
    """Integration tests."""

    def test_full_pipeline(self, temp_db: str) -> None:
        """Test the complete ETL pipeline with mock data."""
        conn = sqlite3.connect(temp_db)
        try:
            conn.execute("PRAGMA journal_mode=WAL;")
            ensure_sqlite_schema(conn, "merenja")

            # Simulate processed DataFrame
            df = pd.DataFrame({
                "izvor_fajl": ["test.xls", "test.xls"],
                "datum_izvestaja": ["2025-12-25", "2025-12-25"],
                "Merni list br": [1, 2],
                "Pošiljalac": ["Sender1", "Sender2"],
                "Poručilac": ["Req1", "Req2"],
                "Primalac": ["Recv1", "Recv2"],
                "Roba": ["Agregat 0-31 mm", "Agregat 0-63 mm"],
                "Bruto": [1000.0, 2000.0],
                "Tara": [400.0, 600.0],
                "Neto": [600.0, 1400.0],
                "Prevoznik": ["Trans1", "Trans2"],
                "Registracija": ["AB-001", "CD-002"],
                "Prikolica": ["", ""],
                "Vozač": ["Driver1", "Driver2"],
                "Broj lične karte": ["ID1", "ID2"],
            })

            rows = df_to_db_rows(df)
            processed, _ = upsert_rows(conn, "merenja", rows)

            assert processed == 2

            # Verify data
            cursor = conn.execute("SELECT SUM(neto) FROM merenja")
            total_weight = cursor.fetchone()[0]
            assert total_weight == 2000.0

        finally:
            conn.close()


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
