"""Text utilities for Miki RKL application."""

from __future__ import annotations

import re


def normalize_whitespace(text: str) -> str:
    """
    Normalize whitespace in text by replacing sequences of whitespace
    characters (spaces, tabs, newlines) with a single space and stripping
    leading/trailing whitespace.

    Args:
        text: Input text to normalize

    Returns:
        Normalized text with single spaces

    Examples:
        >>> normalize_whitespace("  hello   world  ")
        "hello world"
        >>> normalize_whitespace("line1\\n\\nline2")
        "line1 line2"
    """
    return re.sub(r"\s+", " ", text).strip()
