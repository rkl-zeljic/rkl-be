"""Data transformation service for Miki RKL application."""

from __future__ import annotations

from typing import Any, Dict, List

import pandas as pd

from core.constants import DB_COLUMNS, EXPECTED_COLUMNS


def df_to_db_rows(df: pd.DataFrame) -> List[Dict[str, Any]]:
    """
    Convert DataFrame to database row format.

    Maps Excel column names to database column names and converts pandas
    types to Python native types (handles pandas NA → None conversion).

    Args:
        df: DataFrame with extracted measurements (output from extract_measurements_from_excel)

    Returns:
        List of dictionaries, each representing a database row with keys
        matching DB_COLUMNS

    Column mapping:
        Excel columns (EXPECTED_COLUMNS) → Database columns (DB_COLUMNS):
        - Merni list br → merni_list_br
        - Pošiljalac → posiljalac
        - Poručilac → porucilac
        - Primalac → primalac
        - Roba → roba
        - Bruto → bruto
        - Tara → tara
        - Neto → neto
        - Prevoznik → prevoznik
        - Registracija → registracija
        - Prikolica → prikolica
        - Vozač → vozac
        - Broj lične karte → broj_licne_karte

        Metadata columns:
        - izvor_fajl → izvor_fajl
        - datum_izvestaja → datum_izvestaja
    """
    rows = []

    for _, row in df.iterrows():
        db_row = {
            "izvor_fajl": _to_python_value(row.get("izvor_fajl")),
            "datum_izvestaja": _to_python_value(row.get("datum_izvestaja")),
            "merni_list_br": _to_python_value(row.get("Merni list br")),
            "posiljalac": _to_python_value(row.get("Pošiljalac")),
            "porucilac": _to_python_value(row.get("Poručilac")),
            "primalac": _to_python_value(row.get("Primalac")),
            "roba": _to_python_value(row.get("Roba")),
            "bruto": _to_python_value(row.get("Bruto")),
            "tara": _to_python_value(row.get("Tara")),
            "neto": _to_python_value(row.get("Neto")),
            "prevoznik": _to_python_value(row.get("Prevoznik")),
            "registracija": _to_python_value(row.get("Registracija")),
            "prikolica": _to_python_value(row.get("Prikolica")),
            "vozac": _to_python_value(row.get("Vozač")),
            "broj_licne_karte": _to_python_value(row.get("Broj lične karte")),
        }
        rows.append(db_row)

    return rows


def _to_python_value(val: Any) -> Any:
    """
    Convert pandas value to Python native type.

    Handles pandas NA/NaN/NaT → None conversion for database insertion.

    Args:
        val: Value from pandas DataFrame (can be NA, NaN, NaT, or regular value)

    Returns:
        Python native value or None if value is missing/null
    """
    if pd.isna(val):
        return None
    return val
