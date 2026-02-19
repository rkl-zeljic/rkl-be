"""Excel extraction service for Miki RKL application."""

from __future__ import annotations

import os

import pandas as pd

from core.constants import EXPECTED_COLUMNS
from utils.dates import extract_report_date_from_filename
from utils.excel import find_header_row, read_excel_any, to_numeric_series
from utils.text import normalize_whitespace


def extract_measurements_from_excel(path: str) -> pd.DataFrame:
    """
    Extract measurements from Excel file (.xls or .xlsx).

    Reads an Excel file, locates the header row, validates expected columns,
    filters valid measurement rows, performs type conversions, and adds metadata.

    Args:
        path: Path to Excel file

    Returns:
        DataFrame with extracted and cleaned measurements, including:
        - izvor_fajl: Source filename
        - datum_izvestaja: Report date in ISO format (YYYY-MM-DD)
        - All EXPECTED_COLUMNS with cleaned values

    Raises:
        ValueError: If header row not found or expected columns missing
        Exception: If file cannot be read

    Processing steps:
        1. Read Excel file (both .xls and .xlsx supported)
        2. Find header row (scans first 250 rows)
        3. Validate columns (must have "Merni list br" as first column)
        4. Filter valid measurement rows (Merni list br must be numeric)
        5. Type conversions (Int64 for IDs, float for weights, str for text)
        6. Text normalization (remove extra whitespace)
        7. Add metadata (source file, report date)
    """
    # Read Excel file
    df_raw = read_excel_any(path)

    # Find header row
    header_idx = find_header_row(df_raw)

    # Set header and filter data
    df = df_raw.iloc[header_idx:].copy()
    df.columns = df.iloc[0]
    df = df.iloc[1:].reset_index(drop=True)

    # Validate columns
    cols = [c for c in EXPECTED_COLUMNS if c in df.columns]
    if not cols or cols[0] != "Merni list br":
        raise ValueError("Expected columns not found / unexpected header format")

    df = df[cols].copy()

    # Filter valid measurement rows (Merni list br must be numeric)
    df["Merni list br"] = to_numeric_series(df["Merni list br"])
    df = df[df["Merni list br"].notna()].copy()

    if df.empty:
        # No valid measurements found
        df["izvor_fajl"] = None
        df["datum_izvestaja"] = None
        return df

    # Type conversions
    df["Merni list br"] = df["Merni list br"].astype("Int64")

    # Convert weight columns to float
    for col in ["Bruto", "Tara", "Neto"]:
        if col in df.columns:
            df[col] = pd.to_numeric(df[col], errors="coerce")

    # Normalize text columns
    numeric_cols = ["Merni list br", "Bruto", "Tara", "Neto"]
    string_cols = [c for c in df.columns if c not in numeric_cols]

    for col in string_cols:
        df[col] = df[col].astype(str).apply(normalize_whitespace)

    # Add metadata
    df["izvor_fajl"] = os.path.basename(path)
    df["datum_izvestaja"] = extract_report_date_from_filename(path)

    return df
