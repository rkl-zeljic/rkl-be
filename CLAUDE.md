# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

FastAPI-based REST API for importing and querying weighbridge measurement data from Excel files. This is an ETL application for Rudnik Kamena Likodra (RKL) stone quarry that processes daily Excel reports containing measurement records in Serbian.

**Tech stack:** Python 3.13, FastAPI, pandas, SQLite, uvicorn

## Development Commands

### Running the server
```bash
# Development mode with auto-reload
python main.py

# Or using uvicorn directly
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

### Testing
```bash
# Run all tests
pytest

# Run specific test file
pytest tests/test_main.py

# Run with verbose output
pytest -v
```

### Database
```bash
# Query database directly
sqlite3 rkl.db "SELECT COUNT(*) FROM merenja"

# Check recent records
sqlite3 rkl.db "SELECT * FROM merenja ORDER BY created_at DESC LIMIT 5"
```

### Dependencies
```bash
# Install dependencies
pip install -r requirements.txt

# Using virtual environment (recommended)
python -m venv .venv
source .venv/bin/activate  # or .venv/Scripts/activate on Windows
pip install -r requirements.txt
```

## Architecture

### Directory Structure
- `main.py` - FastAPI app initialization, CORS, startup events
- `api/` - REST API layer
  - `routes.py` - Endpoint definitions (import, query)
  - `models.py` - Pydantic request/response schemas
- `services/` - Business logic layer
  - `database.py` - SQLite operations (schema, UPSERT, queries)
  - `extraction.py` - Excel file parsing
  - `transformation.py` - DataFrame to database row mapping
- `core/` - Configuration and constants
  - `config.py` - Environment variables, API settings
  - `constants.py` - Column mappings, header markers
- `utils/` - Helper utilities
  - `excel.py` - Excel reading, header detection
  - `dates.py` - Date extraction from filenames
  - `text.py` - Text normalization
- `tests/` - pytest test suite

### Key Data Flow

**Import flow:**
1. Client uploads Excel file → `POST /api/v1/measurements/import`
2. `routes.py` validates file extension and size
3. `extraction.py` reads Excel, finds header row dynamically, validates columns
4. `transformation.py` maps Excel columns (Serbian) to database columns (snake_case)
5. `database.py` performs UPSERT using unique constraint on `(datum_izvestaja, merni_list_br)`

**Query flow:**
1. Client requests data → `GET /api/v1/measurements`
2. `routes.py` validates query parameters (pagination, filters, sorting)
3. `database.py` builds parameterized SQL with WHERE clause
4. Returns paginated results with metadata

**Customers flow:**
1. Client requests customer list → `GET /api/v1/customers?search=...`
2. `routes.py` passes optional search param
3. `database.py` `query_customers()` groups by `porucilac` with COUNT
4. Returns list of unique customers with measurement counts

### Database Schema

**Table:** `merenja` (measurements)

**Unique constraint:** `(datum_izvestaja, merni_list_br)` - Ensures idempotent imports

**Key columns:**
- `datum_izvestaja` - Report date (YYYY-MM-DD) extracted from filename
- `merni_list_br` - Measurement list number (primary business key)
- Weight columns: `bruto`, `tara`, `neto` (all in kg, REAL type)
- Party columns: `posiljalac`, `porucilac`, `primalac` (sender, requestor, receiver)
- Transport: `registracija`, `prevoznik`, `vozac` (registration, transporter, driver)
- Audit: `created_at`, `updated_at` (automatic timestamps)

**Indices:** `datum_izvestaja`, `roba`, `registracija` (for query performance)

### Column Mapping

Excel columns use Serbian names with diacritics; database columns use snake_case ASCII:

- `Merni list br` → `merni_list_br`
- `Pošiljalac` → `posiljalac`
- `Poručilac` → `porucilac`
- `Primalac` → `primalac`
- `Roba` → `roba`
- `Bruto`/`Tara`/`Neto` → `bruto`/`tara`/`neto`
- `Prevoznik` → `prevoznik`
- `Registracija` → `registracija`
- `Prikolica` → `prikolica`
- `Vozač` → `vozac`
- `Broj lične karte` → `broj_licne_karte`

### Adding New Columns

When adding a new column, update all four locations:
1. `core/constants.py` - `EXPECTED_COLUMNS` (Excel name with diacritics)
2. `core/constants.py` - `DB_COLUMNS` (snake_case database name)
3. `services/database.py` - `ensure_sqlite_schema()` DDL statement
4. `services/transformation.py` - `df_to_db_rows()` mapping function

## Code Style Requirements

### Always Use
- Type hints on all function signatures (required)
- snake_case for functions and variables
- UPPER_CASE for constants
- `_prefix` for private/internal functions
- Parameterized SQL queries (never string interpolation for user data)
- Context managers for file/database operations
- Docstrings with Args/Returns/Raises sections

### Never Use
- Global mutable state
- Hardcoded absolute paths (use environment variables)
- SQL string interpolation for user data (SQL injection risk)
- `print()` for errors (raise exceptions instead)

### Function Signature Template
```python
def function_name(
    required_param: Type,
    optional_param: Optional[Type] = None
) -> ReturnType:
    """Brief description.

    Args:
        required_param: Description
        optional_param: Description

    Returns:
        Description

    Raises:
        ValueError: When validation fails
    """
    pass
```

## Important Patterns

### UPSERT Pattern
Uses SQLite's `ON CONFLICT` clause to update existing records:
- Match key: `(datum_izvestaja, merni_list_br)`
- On insert: Sets `created_at` automatically
- On update: Sets `updated_at` to current timestamp, preserves `created_at`

### Excel Header Detection
Excel files have variable header positions. The code:
1. Scans first 250 rows for marker "Merni list br"
2. Validates that first column is "Merni list br"
3. Filters rows where "Merni list br" is numeric (ignores subtotals, etc.)

### SQL Injection Prevention
- Table names: Validated with regex `^[a-zA-Z_][a-zA-Z0-9_]*$`
- Sort columns: Whitelist validation
- Filter values: Always use parameterized queries with `?` placeholders

## Configuration

Environment variables (all optional, have defaults):
- `RKL_DB_PATH` - Database file path (default: `rkl.db`)
- `RKL_TABLE` - Table name (default: `merenja`)
- `RKL_MAX_FILE_SIZE_MB` - Max upload size (default: `10`)
- `RKL_TEMP_DIR` - Temp directory for uploads (default: `/tmp`)

## API Documentation

Once server is running:
- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc
- Health check: http://localhost:8000/

## Common Issues

### Excel file validation errors
- Ensure file has "Merni list br" header within first 250 rows
- First column must be "Merni list br"
- At least one row must have numeric "Merni list br" value

### Database locked errors
- WAL mode is enabled for better concurrency
- Ensure connections are properly closed (use context managers)

### Date extraction failures
- Filenames must contain date in format `DD.MM.YYYY`
- Example: `25.12.2025. Rudnik kamena Likodra.xls`
