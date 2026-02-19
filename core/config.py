"""Configuration settings for Miki RKL API."""

from __future__ import annotations

import os

# Database configuration
DATABASE_PATH = os.getenv("RKL_DB_PATH", "rkl.db")
DEFAULT_TABLE = os.getenv("RKL_TABLE", "merenja")

# File upload limits
MAX_FILE_SIZE_MB = int(os.getenv("RKL_MAX_FILE_SIZE_MB", "10"))
MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024

# Allowed file extensions
ALLOWED_EXTENSIONS = {".xls", ".xlsx"}

# Temporary directory for file uploads
TEMP_DIR = os.getenv("RKL_TEMP_DIR", "/tmp")

# API configuration
API_TITLE = "Miki RKL API"
API_VERSION = "1.0.0"
API_DESCRIPTION = """
REST API for importing weighbridge Excel reports from Rudnik Kamena Likodra (RKL).

## Features
- Import Excel files (.xls/.xlsx) with measurement data
- Automatic data extraction and validation
- UPSERT into SQLite database (insert or update)
- Idempotent operations (safe to re-import same file)
"""
