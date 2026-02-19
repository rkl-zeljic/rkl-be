"""Constants for Miki RKL ETL application."""

from __future__ import annotations

# Excel column names (expected in source files)
EXPECTED_COLUMNS = [
    "Merni list br",
    "Pošiljalac",
    "Poručilac",
    "Primalac",
    "Roba",
    "Bruto",
    "Tara",
    "Neto",
    "Prevoznik",
    "Registracija",
    "Prikolica",
    "Vozač",
    "Broj lične karte",
]

# Database column names (SQLite table merenja)
DB_COLUMNS = [
    "izvor_fajl",
    "datum_izvestaja",
    "merni_list_br",
    "posiljalac",
    "porucilac",
    "primalac",
    "roba",
    "bruto",
    "tara",
    "neto",
    "prevoznik",
    "registracija",
    "prikolica",
    "vozac",
    "broj_licne_karte",
]

# Header marker for finding data in Excel
HEADER_MARKER = "Merni list br"

# Maximum rows to scan for header
MAX_HEADER_SCAN_ROWS = 250
