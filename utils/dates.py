"""Date utilities for Miki RKL application."""

from __future__ import annotations

import os
import re
from datetime import datetime
from typing import Optional


def extract_report_date_from_filename(path: str) -> Optional[str]:
    """
    Extract report date from filename in dd.mm.yyyy format and convert to ISO format.

    Searches for a date pattern (dd.mm.yyyy) in the filename and validates it.

    Args:
        path: File path containing date in filename

    Returns:
        ISO date string (YYYY-MM-DD) if found and valid, None otherwise

    Examples:
        >>> extract_report_date_from_filename("25.12.2025. Rudnik kamena.xls")
        "2025-12-25"
        >>> extract_report_date_from_filename("Report 01.01.2024.xlsx")
        "2024-01-01"
        >>> extract_report_date_from_filename("invalid.xls")
        None
    """
    basename = os.path.basename(path)
    match = re.search(r"(\d{2})\.(\d{2})\.(\d{4})", basename)

    if not match:
        return None

    day, month, year = match.groups()
    try:
        date_obj = datetime(int(year), int(month), int(day))
        return date_obj.date().isoformat()
    except ValueError:
        # Invalid date (e.g., 32.13.2025)
        return None
