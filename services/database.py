"""Database service for Miki RKL application."""

from __future__ import annotations

import re
import sqlite3
from typing import Any, Dict, List, Optional, Tuple

from core.constants import DB_COLUMNS


def _validate_table_name(table: str) -> None:
    """
    Validate table name to prevent SQL injection.

    Args:
        table: Table name to validate

    Raises:
        ValueError: If table name doesn't match safe pattern
    """
    if not re.match(r"^[a-zA-Z_][a-zA-Z0-9_]*$", table):
        raise ValueError(f"Invalid table name: {table}")


def ensure_sqlite_schema(db_path: str = "rkl.db", table: str = "merenja") -> None:
    """
    Create database schema if it doesn't exist (idempotent).

    Creates the measurements table with appropriate columns, indices, and
    unique constraints. Safe to call multiple times.

    Args:
        db_path: Path to SQLite database file (default: rkl.db)
        table: Table name (default: merenja)

    Raises:
        ValueError: If table name is invalid (SQL injection prevention)
        sqlite3.Error: If database operation fails

    Schema:
        - Primary key: id (autoincrement)
        - Unique constraint: (datum_izvestaja, merni_list_br)
        - Indices: roba, registracija, datum_izvestaja
        - Audit columns: created_at, updated_at
    """
    _validate_table_name(table)

    conn = sqlite3.connect(db_path)
    try:
        # Enable WAL mode for better concurrency
        conn.execute("PRAGMA journal_mode=WAL")
        conn.execute("PRAGMA foreign_keys=ON")

        # Create table
        conn.execute(f"""
        CREATE TABLE IF NOT EXISTS {table} (
            id INTEGER PRIMARY KEY AUTOINCREMENT,

            -- Source tracking
            izvor_fajl TEXT,
            datum_izvestaja TEXT,

            -- Primary measurement ID
            merni_list_br INTEGER NOT NULL,

            -- Parties involved
            posiljalac TEXT,
            porucilac TEXT,
            primalac TEXT,

            -- Cargo
            roba TEXT,

            -- Weights (kg)
            bruto REAL,
            tara REAL,
            neto REAL,

            -- Transport
            prevoznik TEXT,
            registracija TEXT,
            prikolica TEXT,
            vozac TEXT,
            broj_licne_karte TEXT,

            -- Audit
            created_at TEXT DEFAULT (datetime('now')),
            updated_at TEXT
        )
        """)

        # Create unique index for UPSERT
        conn.execute(f"""
        CREATE UNIQUE INDEX IF NOT EXISTS ux_{table}_datum_merni
        ON {table}(datum_izvestaja, merni_list_br)
        """)

        # Create query indices
        conn.execute(f"""
        CREATE INDEX IF NOT EXISTS ix_{table}_roba
        ON {table}(roba)
        """)

        conn.execute(f"""
        CREATE INDEX IF NOT EXISTS ix_{table}_registracija
        ON {table}(registracija)
        """)

        conn.execute(f"""
        CREATE INDEX IF NOT EXISTS ix_{table}_datum
        ON {table}(datum_izvestaja)
        """)

        conn.commit()

        # Add potpis column if it doesn't exist (migration)
        try:
            conn.execute(f"ALTER TABLE {table} ADD COLUMN potpis TEXT")
            conn.commit()
        except sqlite3.OperationalError:
            # Column already exists
            pass
    finally:
        conn.close()


def upsert_rows(
    conn: sqlite3.Connection, table: str, rows: List[Dict[str, Any]]
) -> Tuple[int, int]:
    """
    Insert or update rows (UPSERT) into database table.

    Uses SQLite's ON CONFLICT clause to update existing rows with matching
    (datum_izvestaja, merni_list_br) composite key.

    Args:
        conn: SQLite database connection
        table: Table name
        rows: List of row dictionaries with keys matching DB_COLUMNS

    Returns:
        Tuple of (inserted_count, updated_count)

    Raises:
        ValueError: If table name is invalid
        sqlite3.Error: If database operation fails

    Behavior:
        - New rows: Inserted with created_at set to current timestamp
        - Existing rows: Updated with updated_at set to current timestamp
        - created_at is preserved on updates
    """
    _validate_table_name(table)

    if not rows:
        return (0, 0)

    # Get initial count
    count_before = conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]

    # Prepare UPSERT statement
    columns = DB_COLUMNS
    placeholders = ", ".join(["?" for _ in columns])
    column_list = ", ".join(columns)

    # Build UPDATE SET clause (all columns except the unique key)
    update_columns = [c for c in columns if c not in ["datum_izvestaja", "merni_list_br"]]
    update_set = ", ".join([f"{col}=excluded.{col}" for col in update_columns])
    update_set += ", updated_at=datetime('now')"

    sql = f"""
    INSERT INTO {table} ({column_list})
    VALUES ({placeholders})
    ON CONFLICT(datum_izvestaja, merni_list_br) DO UPDATE SET
    {update_set}
    """

    # Execute batch insert/update
    values = []
    for row in rows:
        row_values = tuple(row.get(col) for col in columns)
        values.append(row_values)

    conn.executemany(sql, values)
    conn.commit()

    # Calculate inserted vs updated
    count_after = conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
    inserted = count_after - count_before
    updated = len(rows) - inserted

    return (inserted, updated)


def get_db_connection(db_path: str = "rkl.db") -> sqlite3.Connection:
    """
    Get SQLite database connection.

    Args:
        db_path: Path to SQLite database file

    Returns:
        SQLite connection object
    """
    conn = sqlite3.connect(db_path)
    conn.execute("PRAGMA foreign_keys=ON")
    # Enable row factory for dict-like access
    conn.row_factory = sqlite3.Row
    return conn


def query_measurements(
    conn: sqlite3.Connection,
    table: str,
    page: int = 1,
    page_size: int = 50,
    datum_od: str | None = None,
    datum_do: str | None = None,
    roba: str | None = None,
    registracija: str | None = None,
    prevoznik: str | None = None,
    posiljalac: str | None = None,
    porucilac: str | None = None,
    primalac: str | None = None,
    vozac: str | None = None,
    sort_by: str = "datum_izvestaja",
    sort_order: str = "DESC",
) -> Tuple[List[Dict[str, Any]], int]:
    """
    Query measurements with filtering, sorting, and pagination.

    Args:
        conn: SQLite database connection
        table: Table name
        page: Page number (1-based, default: 1)
        page_size: Records per page (default: 50)
        datum_od: Filter by report date >= this date (YYYY-MM-DD)
        datum_do: Filter by report date <= this date (YYYY-MM-DD)
        roba: Filter by commodity (partial match, case-insensitive)
        registracija: Filter by vehicle registration (partial match, case-insensitive)
        prevoznik: Filter by transporter (partial match, case-insensitive)
        posiljalac: Filter by sender (partial match, case-insensitive)
        porucilac: Filter by requestor (partial match, case-insensitive)
        primalac: Filter by receiver (partial match, case-insensitive)
        vozac: Filter by driver (partial match, case-insensitive)
        sort_by: Column to sort by (default: datum_izvestaja)
        sort_order: Sort order ASC or DESC (default: DESC)

    Returns:
        Tuple of (records, total_count)
            - records: List of row dictionaries
            - total_count: Total number of records matching filters

    Raises:
        ValueError: If table name or sort parameters are invalid
        sqlite3.Error: If database operation fails
    """
    _validate_table_name(table)

    # Validate sort_by column to prevent SQL injection
    allowed_sort_columns = [
        "id",
        "datum_izvestaja",
        "merni_list_br",
        "roba",
        "neto",
        "bruto",
        "tara",
        "registracija",
        "prevoznik",
        "posiljalac",
        "porucilac",
        "primalac",
        "vozac",
        "created_at",
        "updated_at",
    ]
    if sort_by not in allowed_sort_columns:
        raise ValueError(f"Invalid sort_by column: {sort_by}")

    # Validate sort_order
    if sort_order.upper() not in ["ASC", "DESC"]:
        raise ValueError(f"Invalid sort_order: {sort_order}. Must be ASC or DESC")

    # Build WHERE clause
    where_clauses = []
    params = []

    if datum_od:
        where_clauses.append("datum_izvestaja >= ?")
        params.append(datum_od)

    if datum_do:
        where_clauses.append("datum_izvestaja <= ?")
        params.append(datum_do)

    if roba:
        where_clauses.append("roba LIKE ?")
        params.append(f"%{roba}%")

    if registracija:
        where_clauses.append("registracija LIKE ?")
        params.append(f"%{registracija}%")

    if prevoznik:
        where_clauses.append("prevoznik LIKE ?")
        params.append(f"%{prevoznik}%")

    if posiljalac:
        where_clauses.append("posiljalac LIKE ?")
        params.append(f"%{posiljalac}%")

    if porucilac:
        where_clauses.append("porucilac LIKE ?")
        params.append(f"%{porucilac}%")

    if primalac:
        where_clauses.append("primalac LIKE ?")
        params.append(f"%{primalac}%")

    if vozac:
        where_clauses.append("vozac LIKE ?")
        params.append(f"%{vozac}%")

    where_clause = ""
    if where_clauses:
        where_clause = "WHERE " + " AND ".join(where_clauses)

    # Get total count
    count_sql = f"SELECT COUNT(*) FROM {table} {where_clause}"
    total_count = conn.execute(count_sql, params).fetchone()[0]

    # Calculate offset
    offset = (page - 1) * page_size

    # Query data
    query_sql = f"""
    SELECT
        id,
        izvor_fajl,
        datum_izvestaja,
        merni_list_br,
        posiljalac,
        porucilac,
        primalac,
        roba,
        bruto,
        tara,
        neto,
        prevoznik,
        registracija,
        prikolica,
        vozac,
        broj_licne_karte,
        potpis,
        created_at,
        updated_at
    FROM {table}
    {where_clause}
    ORDER BY {sort_by} {sort_order.upper()}, id {sort_order.upper()}
    LIMIT ? OFFSET ?
    """

    query_params = params + [page_size, offset]
    cursor = conn.execute(query_sql, query_params)

    # Convert rows to dictionaries
    records = []
    for row in cursor.fetchall():
        records.append(dict(row))

    return records, total_count


def query_distinct_values(
    conn: sqlite3.Connection,
    table: str,
    field: str,
    search: Optional[str] = None,
) -> List[str]:
    """
    Query distinct non-empty values for a given field.

    Args:
        conn: SQLite database connection
        table: Table name
        field: Column name (must be in ALLOWED_DISTINCT_FIELDS)
        search: Optional search string for filtering (partial match)

    Returns:
        List of distinct values sorted alphabetically

    Raises:
        ValueError: If table name or field is invalid
        sqlite3.Error: If database operation fails
    """
    _validate_table_name(table)

    allowed_fields = [
        "roba",
        "registracija",
        "prevoznik",
        "primalac",
        "posiljalac",
        "porucilac",
        "vozac",
    ]
    if field not in allowed_fields:
        raise ValueError(f"Invalid field: {field}. Allowed: {', '.join(allowed_fields)}")

    where_clauses = [f"{field} IS NOT NULL", f"{field} != ''"]
    params: list[Any] = []

    if search:
        where_clauses.append(f"{field} LIKE ?")
        params.append(f"%{search}%")

    where_clause = "WHERE " + " AND ".join(where_clauses)

    sql = f"""
    SELECT DISTINCT {field}
    FROM {table}
    {where_clause}
    ORDER BY {field} ASC
    """

    cursor = conn.execute(sql, params)
    return [row[0] for row in cursor.fetchall()]


def query_customers(
    conn: sqlite3.Connection,
    table: str,
    search: Optional[str] = None,
) -> List[Dict[str, Any]]:
    """
    Query distinct customers (porucilac) with measurement counts.

    Args:
        conn: SQLite database connection
        table: Table name
        search: Optional search string for filtering by customer name (partial match)

    Returns:
        List of dicts with 'porucilac' and 'measurement_count' keys

    Raises:
        ValueError: If table name is invalid
        sqlite3.Error: If database operation fails
    """
    _validate_table_name(table)

    where_clauses = ["porucilac IS NOT NULL", "porucilac != ''"]
    params: list[Any] = []

    if search:
        where_clauses.append("porucilac LIKE ?")
        params.append(f"%{search}%")

    where_clause = "WHERE " + " AND ".join(where_clauses)

    sql = f"""
    SELECT porucilac, COUNT(*) as measurement_count
    FROM {table}
    {where_clause}
    GROUP BY porucilac
    ORDER BY porucilac ASC
    """

    cursor = conn.execute(sql, params)
    return [dict(row) for row in cursor.fetchall()]


def update_signature(
    conn: sqlite3.Connection,
    table: str,
    measurement_id: int,
    signature: Optional[str],
) -> bool:
    """
    Update signature for a measurement record.

    Args:
        conn: SQLite database connection
        table: Table name
        measurement_id: ID of the measurement to update
        signature: Base64 PNG data URL, or None to clear

    Returns:
        True if a row was updated, False if no matching row found

    Raises:
        ValueError: If table name is invalid
        sqlite3.Error: If database operation fails
    """
    _validate_table_name(table)

    cursor = conn.execute(
        f"UPDATE {table} SET potpis = ?, updated_at = datetime('now') WHERE id = ?",
        (signature, measurement_id),
    )
    conn.commit()

    return cursor.rowcount > 0


def query_measurement_stats(
    conn: sqlite3.Connection,
    table: str,
    group_by: str,
    datum_od: str | None = None,
    datum_do: str | None = None,
    porucilac: str | None = None,
    vozac: str | None = None,
    roba: str | None = None,
) -> List[Dict[str, Any]]:
    """
    Query measurement statistics grouped by time period.

    Args:
        conn: SQLite database connection
        table: Table name
        group_by: Grouping mode - 'day', 'week', 'month', or 'year'
        datum_od: Filter by report date >= this date (YYYY-MM-DD)
        datum_do: Filter by report date <= this date (YYYY-MM-DD)
        porucilac: Filter by customer (partial match)
        vozac: Filter by driver (partial match)
        roba: Filter by commodity (partial match)

    Returns:
        List of dicts with 'period' and 'count' keys, ordered by period ASC

    Raises:
        ValueError: If table name or group_by is invalid
    """
    _validate_table_name(table)

    allowed_group_by = ["day", "week", "month", "year"]
    if group_by not in allowed_group_by:
        raise ValueError(f"Invalid group_by: {group_by}. Allowed: {', '.join(allowed_group_by)}")

    # Build GROUP BY expression based on mode
    if group_by == "day":
        period_expr = "datum_izvestaja"
    elif group_by == "week":
        # ISO week: YYYY-Www
        period_expr = "strftime('%Y-W%W', datum_izvestaja)"
    elif group_by == "month":
        period_expr = "strftime('%Y-%m', datum_izvestaja)"
    else:  # year
        period_expr = "strftime('%Y', datum_izvestaja)"

    # Build WHERE clause
    where_clauses = ["datum_izvestaja IS NOT NULL", "datum_izvestaja != ''"]
    params: list[Any] = []

    if datum_od:
        where_clauses.append("datum_izvestaja >= ?")
        params.append(datum_od)

    if datum_do:
        where_clauses.append("datum_izvestaja <= ?")
        params.append(datum_do)

    if porucilac:
        where_clauses.append("porucilac LIKE ?")
        params.append(f"%{porucilac}%")

    if vozac:
        where_clauses.append("vozac LIKE ?")
        params.append(f"%{vozac}%")

    if roba:
        where_clauses.append("roba LIKE ?")
        params.append(f"%{roba}%")

    where_clause = "WHERE " + " AND ".join(where_clauses)

    sql = f"""
    SELECT {period_expr} as period, COUNT(*) as count
    FROM {table}
    {where_clause}
    GROUP BY period
    ORDER BY period ASC
    """

    cursor = conn.execute(sql, params)
    return [dict(row) for row in cursor.fetchall()]


def query_drivers(
    conn: sqlite3.Connection,
    table: str,
    search: Optional[str] = None,
) -> List[Dict[str, Any]]:
    """
    Query distinct drivers (vozac) with measurement counts.

    Args:
        conn: SQLite database connection
        table: Table name
        search: Optional search string for filtering by driver name (partial match)

    Returns:
        List of dicts with 'vozac' and 'measurement_count' keys

    Raises:
        ValueError: If table name is invalid
        sqlite3.Error: If database operation fails
    """
    _validate_table_name(table)

    where_clauses = ["vozac IS NOT NULL", "vozac != ''"]
    params: list[Any] = []

    if search:
        where_clauses.append("vozac LIKE ?")
        params.append(f"%{search}%")

    where_clause = "WHERE " + " AND ".join(where_clauses)

    sql = f"""
    SELECT vozac, COUNT(*) as measurement_count
    FROM {table}
    {where_clause}
    GROUP BY vozac
    ORDER BY vozac ASC
    """

    cursor = conn.execute(sql, params)
    return [dict(row) for row in cursor.fetchall()]
