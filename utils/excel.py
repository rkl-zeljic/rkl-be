"""Excel utilities for Miki RKL application."""

from __future__ import annotations

import pandas as pd

from core.constants import HEADER_MARKER, MAX_HEADER_SCAN_ROWS


def read_excel_any(path: str) -> pd.DataFrame:
    """
    Read Excel file (.xls or .xlsx) using appropriate engine.

    Uses xlrd for .xls files and openpyxl for .xlsx files.

    Args:
        path: Path to Excel file

    Returns:
        DataFrame with all data from the Excel file

    Raises:
        Exception: If file cannot be read or is corrupted
    """
    if path.endswith(".xls"):
        return pd.read_excel(path, engine="xlrd", header=None)
    else:
        return pd.read_excel(path, engine="openpyxl", header=None)


def find_header_row(df: pd.DataFrame) -> int:
    """
    Find the header row containing the measurement marker in Excel data.

    Scans the first MAX_HEADER_SCAN_ROWS rows looking for the header marker
    (default: "Merni list br") to locate where actual data begins.

    Args:
        df: DataFrame with raw Excel data (no header)

    Returns:
        Zero-based row index where the header is found

    Raises:
        ValueError: If header marker not found in first MAX_HEADER_SCAN_ROWS rows
    """
    scan_limit = min(len(df), MAX_HEADER_SCAN_ROWS)

    for i in range(scan_limit):
        row_values = df.iloc[i].astype(str).values
        if any(HEADER_MARKER in str(val) for val in row_values):
            return i

    raise ValueError(
        f"Header row with marker '{HEADER_MARKER}' not found "
        f"in first {scan_limit} rows"
    )


def to_numeric_series(series: pd.Series) -> pd.Series:
    """
    Convert Series to numeric, coercing errors to NaN.

    Args:
        series: Input pandas Series

    Returns:
        Series with numeric values, non-numeric values become NaN
    """
    return pd.to_numeric(series, errors="coerce")
