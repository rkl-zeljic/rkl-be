"""API routes for Miki RKL application."""

from __future__ import annotations

import os
import time
from pathlib import Path

from typing import Optional

from fastapi import APIRouter, File, Form, HTTPException, Query, UploadFile

from api.models import CustomersResponse, DriversResponse, DistinctValuesResponse, ErrorResponse, ImportResponse, MeasurementsResponse, PaginationMeta, SignatureRequest, SignatureResponse, StatsResponse
from core.config import ALLOWED_EXTENSIONS, DATABASE_PATH, DEFAULT_TABLE, MAX_FILE_SIZE_BYTES
from services.database import ensure_sqlite_schema, get_db_connection, query_customers, query_distinct_values, query_drivers, query_measurement_stats, query_measurements, update_signature, upsert_rows
from services.extraction import extract_measurements_from_excel
from services.transformation import df_to_db_rows

router = APIRouter(prefix="/api/v1", tags=["measurements"])


@router.post(
    "/measurements/import",
    response_model=ImportResponse,
    responses={
        400: {"model": ErrorResponse, "description": "Bad request (validation error)"},
        413: {"model": ErrorResponse, "description": "File too large"},
        500: {"model": ErrorResponse, "description": "Internal server error"},
    },
    summary="Import measurements from Excel file",
    description="""
    Upload an Excel file (.xls or .xlsx) containing weighbridge measurements
    and import them into the SQLite database.

    The import is idempotent - uploading the same file multiple times will
    update existing records rather than creating duplicates.

    Records are matched by (datum_izvestaja, merni_list_br) composite key.
    """,
)
async def import_measurements(
    file: UploadFile = File(
        ...,
        description="Excel file (.xls or .xlsx) with measurement data",
    ),
    db: str = Form(
        default=DATABASE_PATH,
        description="Database file path (default: rkl.db)",
    ),
    table: str = Form(
        default=DEFAULT_TABLE,
        description="Table name (default: merenja)",
    ),
) -> ImportResponse:
    """
    Import measurements from uploaded Excel file.

    Processing steps:
    1. Validate file extension and size
    2. Save uploaded file to temporary location
    3. Extract measurements from Excel (pandas)
    4. Transform to database row format
    5. UPSERT into SQLite database
    6. Return statistics (inserted, updated counts)
    """
    start_time = time.time()

    # Validate file extension
    file_ext = Path(file.filename or "").suffix.lower()
    if file_ext not in ALLOWED_EXTENSIONS:
        raise HTTPException(
            status_code=400,
            detail={
                "status": "error",
                "message": f"Invalid file type. Allowed: {', '.join(ALLOWED_EXTENSIONS)}",
                "details": {"filename": file.filename, "error_type": "ValidationError"},
            },
        )

    # Read file content and validate size
    file_content = await file.read()
    file_size = len(file_content)

    if file_size > MAX_FILE_SIZE_BYTES:
        raise HTTPException(
            status_code=413,
            detail={
                "status": "error",
                "message": f"File size ({file_size / 1024 / 1024:.1f} MB) exceeds limit ({MAX_FILE_SIZE_BYTES / 1024 / 1024:.1f} MB)",
                "details": {"filename": file.filename, "error_type": "FileTooLarge"},
            },
        )

    # Save to temporary file
    temp_path = f"/tmp/rkl_upload_{int(time.time() * 1000)}_{file.filename}"
    try:
        with open(temp_path, "wb") as f:
            f.write(file_content)

        # Ensure database schema exists
        ensure_sqlite_schema(db_path=db, table=table)

        # Extract measurements from Excel
        try:
            df = extract_measurements_from_excel(temp_path)
        except ValueError as e:
            raise HTTPException(
                status_code=400,
                detail={
                    "status": "error",
                    "message": str(e),
                    "details": {
                        "filename": file.filename,
                        "error_type": "ValidationError",
                    },
                },
            )
        except Exception as e:
            raise HTTPException(
                status_code=400,
                detail={
                    "status": "error",
                    "message": f"Failed to parse Excel file: {str(e)}",
                    "details": {
                        "filename": file.filename,
                        "error_type": "ExcelParseError",
                    },
                },
            )

        # Transform to database rows
        rows = df_to_db_rows(df)

        if not rows:
            return ImportResponse(
                status="success",
                inserted=0,
                updated=0,
                total_rows=0,
                filename=file.filename or "unknown",
                processing_time_ms=int((time.time() - start_time) * 1000),
                errors=["No valid measurements found in file"],
            )

        # UPSERT into database
        conn = get_db_connection(db_path=db)
        try:
            inserted, updated = upsert_rows(conn, table, rows)
        except Exception as e:
            raise HTTPException(
                status_code=500,
                detail={
                    "status": "error",
                    "message": f"Database error: {str(e)}",
                    "details": {
                        "filename": file.filename,
                        "error_type": "DatabaseError",
                    },
                },
            )
        finally:
            conn.close()

        # Calculate processing time
        processing_time_ms = int((time.time() - start_time) * 1000)

        return ImportResponse(
            status="success",
            inserted=inserted,
            updated=updated,
            total_rows=len(rows),
            filename=file.filename or "unknown",
            processing_time_ms=processing_time_ms,
        )

    finally:
        # Clean up temporary file
        if os.path.exists(temp_path):
            os.remove(temp_path)


@router.get(
    "/measurements",
    response_model=MeasurementsResponse,
    responses={
        400: {"model": ErrorResponse, "description": "Bad request (invalid parameters)"},
        500: {"model": ErrorResponse, "description": "Internal server error"},
    },
    summary="Query measurements with filtering and pagination",
    description="""
    Retrieve measurements from the database with optional filtering, sorting, and pagination.

    Supports:
    - Date range filtering (datum_od, datum_do)
    - Text filtering (roba, registracija, prevoznik, posiljalac, primalac) - partial match, case-insensitive
    - Sorting by any indexed column (default: datum_izvestaja DESC)
    - Pagination (page, page_size)

    All filter parameters are optional. Without filters, returns all records.
    """,
)
async def get_measurements(
    db: str = Query(
        default=DATABASE_PATH,
        description="Database file path (default: rkl.db)",
    ),
    table: str = Query(
        default=DEFAULT_TABLE,
        description="Table name (default: merenja)",
    ),
    page: int = Query(
        default=1,
        ge=1,
        description="Page number (1-based)",
    ),
    page_size: int = Query(
        default=50,
        ge=1,
        le=1000,
        description="Records per page (max: 1000)",
    ),
    datum_od: Optional[str] = Query(
        default=None,
        description="Filter by report date >= this date (YYYY-MM-DD)",
        pattern=r"^\d{4}-\d{2}-\d{2}$",
    ),
    datum_do: Optional[str] = Query(
        default=None,
        description="Filter by report date <= this date (YYYY-MM-DD)",
        pattern=r"^\d{4}-\d{2}-\d{2}$",
    ),
    roba: Optional[str] = Query(
        default=None,
        description="Filter by commodity (partial match, case-insensitive)",
    ),
    registracija: Optional[str] = Query(
        default=None,
        description="Filter by vehicle registration (partial match, case-insensitive)",
    ),
    prevoznik: Optional[str] = Query(
        default=None,
        description="Filter by transporter (partial match, case-insensitive)",
    ),
    posiljalac: Optional[str] = Query(
        default=None,
        description="Filter by sender (partial match, case-insensitive)",
    ),
    porucilac: Optional[str] = Query(
        default=None,
        description="Filter by requestor (partial match, case-insensitive)",
    ),
    primalac: Optional[str] = Query(
        default=None,
        description="Filter by receiver (partial match, case-insensitive)",
    ),
    vozac: Optional[str] = Query(
        default=None,
        description="Filter by driver (partial match, case-insensitive)",
    ),
    sort_by: str = Query(
        default="datum_izvestaja",
        description="Column to sort by",
        pattern=r"^[a-z_]+$",
    ),
    sort_order: str = Query(
        default="DESC",
        description="Sort order (ASC or DESC)",
        pattern=r"^(ASC|DESC)$",
    ),
) -> MeasurementsResponse:
    """
    Query measurements with filtering, sorting, and pagination.

    Processing steps:
    1. Validate query parameters
    2. Build SQL query with WHERE clause from filters
    3. Get total count of matching records
    4. Query paginated data with sorting
    5. Return records with pagination metadata
    """
    # Get database connection
    conn = get_db_connection(db_path=db)

    try:
        # Query measurements
        records, total_count = query_measurements(
            conn=conn,
            table=table,
            page=page,
            page_size=page_size,
            datum_od=datum_od,
            datum_do=datum_do,
            roba=roba,
            registracija=registracija,
            prevoznik=prevoznik,
            posiljalac=posiljalac,
            porucilac=porucilac,
            primalac=primalac,
            vozac=vozac,
            sort_by=sort_by,
            sort_order=sort_order,
        )
    except ValueError as e:
        raise HTTPException(
            status_code=400,
            detail={
                "status": "error",
                "message": str(e),
                "details": {"error_type": "ValidationError"},
            },
        )
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail={
                "status": "error",
                "message": f"Database error: {str(e)}",
                "details": {"error_type": "DatabaseError"},
            },
        )
    finally:
        conn.close()

    # Calculate pagination metadata
    total_pages = (total_count + page_size - 1) // page_size

    # Build filters_applied dict
    filters_applied = {}
    if datum_od:
        filters_applied["datum_od"] = datum_od
    if datum_do:
        filters_applied["datum_do"] = datum_do
    if roba:
        filters_applied["roba"] = roba
    if registracija:
        filters_applied["registracija"] = registracija
    if prevoznik:
        filters_applied["prevoznik"] = prevoznik
    if posiljalac:
        filters_applied["posiljalac"] = posiljalac
    if primalac:
        filters_applied["primalac"] = primalac
    filters_applied["sort_by"] = sort_by
    filters_applied["sort_order"] = sort_order

    return MeasurementsResponse(
        status="success",
        data=records,
        pagination=PaginationMeta(
            total_count=total_count,
            page=page,
            page_size=page_size,
            total_pages=total_pages,
        ),
        filters_applied=filters_applied,
    )


@router.get(
    "/measurements/stats",
    response_model=StatsResponse,
    responses={
        400: {"model": ErrorResponse, "description": "Bad request (invalid parameters)"},
        500: {"model": ErrorResponse, "description": "Internal server error"},
    },
    summary="Get measurement statistics grouped by time period",
    description="""
    Retrieve measurement counts grouped by time period (day, week, month, year).

    Supports optional date range and text filters.
    """,
)
async def get_measurement_stats(
    group_by: str = Query(
        ...,
        description="Grouping mode: day, week, month, or year",
        pattern=r"^(day|week|month|year)$",
    ),
    db: str = Query(
        default=DATABASE_PATH,
        description="Database file path (default: rkl.db)",
    ),
    table: str = Query(
        default=DEFAULT_TABLE,
        description="Table name (default: merenja)",
    ),
    datum_od: Optional[str] = Query(
        default=None,
        description="Filter by report date >= this date (YYYY-MM-DD)",
        pattern=r"^\d{4}-\d{2}-\d{2}$",
    ),
    datum_do: Optional[str] = Query(
        default=None,
        description="Filter by report date <= this date (YYYY-MM-DD)",
        pattern=r"^\d{4}-\d{2}-\d{2}$",
    ),
    porucilac: Optional[str] = Query(
        default=None,
        description="Filter by customer (partial match, case-insensitive)",
    ),
    vozac: Optional[str] = Query(
        default=None,
        description="Filter by driver (partial match, case-insensitive)",
    ),
    roba: Optional[str] = Query(
        default=None,
        description="Filter by commodity (partial match, case-insensitive)",
    ),
) -> StatsResponse:
    """Get measurement statistics grouped by time period."""
    conn = get_db_connection(db_path=db)

    try:
        data = query_measurement_stats(
            conn=conn,
            table=table,
            group_by=group_by,
            datum_od=datum_od,
            datum_do=datum_do,
            porucilac=porucilac,
            vozac=vozac,
            roba=roba,
        )
    except ValueError as e:
        raise HTTPException(
            status_code=400,
            detail={
                "status": "error",
                "message": str(e),
                "details": {"error_type": "ValidationError"},
            },
        )
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail={
                "status": "error",
                "message": f"Database error: {str(e)}",
                "details": {"error_type": "DatabaseError"},
            },
        )
    finally:
        conn.close()

    total_count = sum(item["count"] for item in data)

    return StatsResponse(
        status="success",
        group_by=group_by,
        data=data,
        total_count=total_count,
    )


@router.get(
    "/measurements/distinct/{field}",
    response_model=DistinctValuesResponse,
    responses={
        400: {"model": ErrorResponse, "description": "Bad request (invalid field)"},
        500: {"model": ErrorResponse, "description": "Internal server error"},
    },
    summary="Get distinct values for a measurement field",
    description="""
    Retrieve distinct non-empty values for a specific measurement field.

    Useful for populating searchable dropdown filters.
    Supports optional text search filtering on values.

    Allowed fields: roba, registracija, prevoznik, primalac, posiljalac, porucilac, vozac
    """,
)
async def get_distinct_values(
    field: str,
    db: str = Query(
        default=DATABASE_PATH,
        description="Database file path (default: rkl.db)",
    ),
    table: str = Query(
        default=DEFAULT_TABLE,
        description="Table name (default: merenja)",
    ),
    search: Optional[str] = Query(
        default=None,
        description="Search filter for values (partial match, case-insensitive)",
    ),
) -> DistinctValuesResponse:
    """Get distinct values for a measurement field."""
    conn = get_db_connection(db_path=db)

    try:
        values = query_distinct_values(conn=conn, table=table, field=field, search=search)
    except ValueError as e:
        raise HTTPException(
            status_code=400,
            detail={
                "status": "error",
                "message": str(e),
                "details": {"error_type": "ValidationError"},
            },
        )
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail={
                "status": "error",
                "message": f"Database error: {str(e)}",
                "details": {"error_type": "DatabaseError"},
            },
        )
    finally:
        conn.close()

    return DistinctValuesResponse(
        status="success",
        field=field,
        data=values,
        total_count=len(values),
    )


@router.get(
    "/customers",
    response_model=CustomersResponse,
    responses={
        400: {"model": ErrorResponse, "description": "Bad request (invalid parameters)"},
        500: {"model": ErrorResponse, "description": "Internal server error"},
    },
    summary="Get unique customers with measurement counts",
    description="""
    Retrieve a list of unique customers (porucilac) with their measurement counts.

    Supports optional text search filtering on customer name.
    """,
)
async def get_customers(
    db: str = Query(
        default=DATABASE_PATH,
        description="Database file path (default: rkl.db)",
    ),
    table: str = Query(
        default=DEFAULT_TABLE,
        description="Table name (default: merenja)",
    ),
    search: Optional[str] = Query(
        default=None,
        description="Search filter for customer name (partial match, case-insensitive)",
    ),
) -> CustomersResponse:
    """Get unique customers with measurement counts."""
    conn = get_db_connection(db_path=db)

    try:
        records = query_customers(conn=conn, table=table, search=search)
    except ValueError as e:
        raise HTTPException(
            status_code=400,
            detail={
                "status": "error",
                "message": str(e),
                "details": {"error_type": "ValidationError"},
            },
        )
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail={
                "status": "error",
                "message": f"Database error: {str(e)}",
                "details": {"error_type": "DatabaseError"},
            },
        )
    finally:
        conn.close()

    return CustomersResponse(
        status="success",
        data=records,
        total_count=len(records),
    )


@router.get(
    "/drivers",
    response_model=DriversResponse,
    responses={
        400: {"model": ErrorResponse, "description": "Bad request (invalid parameters)"},
        500: {"model": ErrorResponse, "description": "Internal server error"},
    },
    summary="Get unique drivers with measurement counts",
    description="""
    Retrieve a list of unique drivers (vozac) with their measurement counts.

    Supports optional text search filtering on driver name.
    """,
)
async def get_drivers(
    db: str = Query(
        default=DATABASE_PATH,
        description="Database file path (default: rkl.db)",
    ),
    table: str = Query(
        default=DEFAULT_TABLE,
        description="Table name (default: merenja)",
    ),
    search: Optional[str] = Query(
        default=None,
        description="Search filter for driver name (partial match, case-insensitive)",
    ),
) -> DriversResponse:
    """Get unique drivers with measurement counts."""
    conn = get_db_connection(db_path=db)

    try:
        records = query_drivers(conn=conn, table=table, search=search)
    except ValueError as e:
        raise HTTPException(
            status_code=400,
            detail={
                "status": "error",
                "message": str(e),
                "details": {"error_type": "ValidationError"},
            },
        )
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail={
                "status": "error",
                "message": f"Database error: {str(e)}",
                "details": {"error_type": "DatabaseError"},
            },
        )
    finally:
        conn.close()

    return DriversResponse(
        status="success",
        data=records,
        total_count=len(records),
    )


@router.patch(
    "/measurements/{measurement_id}/signature",
    response_model=SignatureResponse,
    responses={
        404: {"model": ErrorResponse, "description": "Measurement not found"},
        400: {"model": ErrorResponse, "description": "Invalid signature data"},
        500: {"model": ErrorResponse, "description": "Internal server error"},
    },
    summary="Save or clear a measurement signature",
)
async def save_signature(
    measurement_id: int,
    body: SignatureRequest,
    db: str = Query(
        default=DATABASE_PATH,
        description="Database file path (default: rkl.db)",
    ),
    table: str = Query(
        default=DEFAULT_TABLE,
        description="Table name (default: merenja)",
    ),
) -> SignatureResponse:
    """Save or clear a signature for a specific measurement."""
    # Validate base64 data URL format if provided
    if body.signature and not body.signature.startswith("data:image/png;base64,"):
        raise HTTPException(
            status_code=400,
            detail={
                "status": "error",
                "message": "Invalid signature format. Expected base64 PNG data URL.",
                "details": {"error_type": "ValidationError"},
            },
        )

    conn = get_db_connection(db_path=db)
    try:
        updated = update_signature(
            conn=conn,
            table=table,
            measurement_id=measurement_id,
            signature=body.signature,
        )
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail={
                "status": "error",
                "message": f"Database error: {str(e)}",
                "details": {"error_type": "DatabaseError"},
            },
        )
    finally:
        conn.close()

    if not updated:
        raise HTTPException(
            status_code=404,
            detail={
                "status": "error",
                "message": f"Measurement with id {measurement_id} not found",
                "details": {"error_type": "NotFound"},
            },
        )

    return SignatureResponse(
        status="success",
        measurement_id=measurement_id,
        has_signature=body.signature is not None,
    )
