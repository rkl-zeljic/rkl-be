"""Miki RKL API - FastAPI application for weighbridge data import."""

from __future__ import annotations

import sqlite3

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from api.models import HealthResponse
from api.routes import router
from core.config import API_DESCRIPTION, API_TITLE, API_VERSION, DATABASE_PATH, DEFAULT_TABLE
from services.database import ensure_sqlite_schema

# Create FastAPI app
app = FastAPI(
    title=API_TITLE,
    version=API_VERSION,
    description=API_DESCRIPTION,
    docs_url="/docs",
    redoc_url="/redoc",
)

# Add CORS middleware to allow frontend access
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:5173",  # Vite dev server
        "http://localhost:3000",  # Alternative dev server
        "http://127.0.0.1:5173",
        "http://127.0.0.1:3000",
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include API routes
app.include_router(router)


@app.on_event("startup")
async def startup_event():
    """Initialize database schema on application startup."""
    ensure_sqlite_schema(db_path=DATABASE_PATH, table=DEFAULT_TABLE)


@app.get(
    "/",
    response_model=HealthResponse,
    summary="Health check",
    description="Check API health and database connectivity",
)
async def root() -> HealthResponse:
    """
    Health check endpoint.

    Returns service status and database connectivity.
    """
    # Check database connectivity
    db_status = "connected"
    try:
        conn = sqlite3.connect(DATABASE_PATH)
        conn.execute("SELECT 1")
        conn.close()
    except Exception:
        db_status = "error"

    return HealthResponse(
        status="ok",
        service=API_TITLE,
        version=API_VERSION,
        database=db_status,
    )


@app.exception_handler(Exception)
async def global_exception_handler(request, exc):
    """
    Global exception handler for unhandled errors.
    """
    return JSONResponse(
        status_code=500,
        content={
            "status": "error",
            "message": "Internal server error",
            "details": {"error_type": type(exc).__name__},
        },
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8000,
        reload=True,
        log_level="info",
    )
