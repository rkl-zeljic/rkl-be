"""Pydantic models for Miki RKL API request/response schemas."""

from __future__ import annotations

from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


class ImportResponse(BaseModel):
    """Response model for measurement import endpoint."""

    status: str = Field(
        default="success",
        description="Status of the operation (success or error)",
        examples=["success"],
    )
    inserted: int = Field(
        ge=0,
        description="Number of new measurements inserted",
        examples=[15],
    )
    updated: int = Field(
        ge=0,
        description="Number of existing measurements updated",
        examples=[3],
    )
    total_rows: int = Field(
        ge=0,
        description="Total number of rows processed",
        examples=[18],
    )
    filename: str = Field(
        description="Name of the uploaded file",
        examples=["25.12.2025. Rudnik kamena Likodra.xls"],
    )
    processing_time_ms: int = Field(
        ge=0,
        description="Processing time in milliseconds",
        examples=[245],
    )
    errors: Optional[List[str]] = Field(
        default=None,
        description="List of non-fatal errors or warnings",
        examples=[None],
    )


class ErrorResponse(BaseModel):
    """Standard error response model."""

    status: str = Field(
        default="error",
        description="Status of the operation (always 'error' for this model)",
        examples=["error"],
    )
    message: str = Field(
        description="Human-readable error message",
        examples=["Header row with marker 'Merni list br' not found"],
    )
    details: Optional[dict] = Field(
        default=None,
        description="Additional error details",
        examples=[{"filename": "invalid.xls", "error_type": "ValidationError"}],
    )


class HealthResponse(BaseModel):
    """Health check response model."""

    status: str = Field(
        default="ok",
        description="Health status",
        examples=["ok"],
    )
    service: str = Field(
        description="Service name",
        examples=["Miki RKL API"],
    )
    version: str = Field(
        description="API version",
        examples=["1.0.0"],
    )
    database: str = Field(
        description="Database status",
        examples=["connected"],
    )


class MeasurementRecord(BaseModel):
    """Single measurement record model."""

    id: int = Field(description="Measurement ID")
    izvor_fajl: Optional[str] = Field(default=None, description="Source filename")
    datum_izvestaja: Optional[str] = Field(default=None, description="Report date (YYYY-MM-DD)")
    merni_list_br: int = Field(description="Measurement list number")
    posiljalac: Optional[str] = Field(default=None, description="Sender")
    porucilac: Optional[str] = Field(default=None, description="Requestor")
    primalac: Optional[str] = Field(default=None, description="Receiver")
    roba: Optional[str] = Field(default=None, description="Commodity/goods")
    bruto: Optional[float] = Field(default=None, description="Gross weight (kg)")
    tara: Optional[float] = Field(default=None, description="Tare weight (kg)")
    neto: Optional[float] = Field(default=None, description="Net weight (kg)")
    prevoznik: Optional[str] = Field(default=None, description="Transporter")
    registracija: Optional[str] = Field(default=None, description="Vehicle registration")
    prikolica: Optional[str] = Field(default=None, description="Trailer")
    vozac: Optional[str] = Field(default=None, description="Driver")
    broj_licne_karte: Optional[str] = Field(default=None, description="Driver ID number")
    potpis: Optional[str] = Field(default=None, description="Signature as base64 PNG data URL")
    created_at: Optional[str] = Field(default=None, description="Created timestamp")
    updated_at: Optional[str] = Field(default=None, description="Updated timestamp")

    class Config:
        from_attributes = True


class PaginationMeta(BaseModel):
    """Pagination metadata model."""

    total_count: int = Field(ge=0, description="Total number of records matching filters")
    page: int = Field(ge=1, description="Current page number")
    page_size: int = Field(ge=1, description="Number of records per page")
    total_pages: int = Field(ge=0, description="Total number of pages")


class MeasurementsResponse(BaseModel):
    """Response model for measurements query endpoint."""

    status: str = Field(
        default="success",
        description="Status of the operation",
        examples=["success"],
    )
    data: List[MeasurementRecord] = Field(
        description="List of measurement records",
    )
    pagination: PaginationMeta = Field(
        description="Pagination metadata",
    )
    filters_applied: Dict[str, Any] = Field(
        default_factory=dict,
        description="Filters applied to the query",
    )


class DistinctValuesResponse(BaseModel):
    """Response model for distinct field values endpoint."""

    status: str = Field(
        default="success",
        description="Status of the operation",
        examples=["success"],
    )
    field: str = Field(
        description="The field name queried",
        examples=["roba"],
    )
    data: List[str] = Field(
        description="List of distinct values",
    )
    total_count: int = Field(
        ge=0,
        description="Total number of distinct values",
    )


class CustomerRecord(BaseModel):
    """Single customer (porucilac) record with measurement count."""

    porucilac: str = Field(description="Customer name (porucilac)")
    measurement_count: int = Field(ge=0, description="Number of measurements for this customer")


class CustomersResponse(BaseModel):
    """Response model for customers query endpoint."""

    status: str = Field(
        default="success",
        description="Status of the operation",
        examples=["success"],
    )
    data: List[CustomerRecord] = Field(
        description="List of customer records",
    )
    total_count: int = Field(
        ge=0,
        description="Total number of unique customers",
    )


class DriverRecord(BaseModel):
    """Single driver (vozac) record with measurement count."""

    vozac: str = Field(description="Driver name (vozac)")
    measurement_count: int = Field(ge=0, description="Number of measurements for this driver")


class DriversResponse(BaseModel):
    """Response model for drivers query endpoint."""

    status: str = Field(
        default="success",
        description="Status of the operation",
        examples=["success"],
    )
    data: List[DriverRecord] = Field(
        description="List of driver records",
    )
    total_count: int = Field(
        ge=0,
        description="Total number of unique drivers",
    )


class StatsDataPoint(BaseModel):
    """Single data point for measurement statistics."""

    period: str = Field(description="Time period label (e.g. '2025-01', '2025-W12', '2025-03-15', '2025')")
    count: int = Field(ge=0, description="Number of measurements in this period")


class StatsResponse(BaseModel):
    """Response model for measurement statistics endpoint."""

    status: str = Field(
        default="success",
        description="Status of the operation",
        examples=["success"],
    )
    group_by: str = Field(description="Grouping mode used (day, week, month, year)")
    data: List[StatsDataPoint] = Field(description="List of period-count data points")
    total_count: int = Field(ge=0, description="Sum of all counts")


class SignatureRequest(BaseModel):
    """Request model for saving a signature."""

    signature: Optional[str] = Field(
        default=None,
        max_length=500_000,
        description="Signature as base64 PNG data URL, or null to clear",
    )


class SignatureResponse(BaseModel):
    """Response model for signature save endpoint."""

    status: str = Field(
        default="success",
        description="Status of the operation",
        examples=["success"],
    )
    measurement_id: int = Field(description="ID of the updated measurement")
    has_signature: bool = Field(description="Whether the measurement now has a signature")
