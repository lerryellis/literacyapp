"""Shared analytics helpers used by both the API views and the admin."""

import csv
import re
from collections import Counter, defaultdict

from django.http import HttpResponse


def aggregate_difficult_words(sessions):
    """Given a Session queryset, return difficult words ranked by how often
    learners struggled with them.

    Returns a list of dicts: {word, occurrences, student_count} sorted by
    occurrences descending. Words are normalised to lowercase so "Because"
    and "because" are counted together.
    """
    occurrences = Counter()
    students_per_word = defaultdict(set)

    for row in sessions.values("student_id", "difficult_words"):
        words = row["difficult_words"] or []
        if not isinstance(words, list):
            continue
        for raw in words:
            word = str(raw).strip().lower()
            if not word:
                continue
            occurrences[word] += 1
            students_per_word[word].add(row["student_id"])

    return [
        {
            "word": word,
            "occurrences": count,
            "student_count": len(students_per_word[word]),
        }
        for word, count in occurrences.most_common()
    ]


def csv_response(filename_stem, header, rows):
    """Build a downloadable CSV HttpResponse from a header and row iterable."""
    safe_stem = re.sub(r"[^A-Za-z0-9._-]+", "_", filename_stem).strip("_") or "export"
    response = HttpResponse(content_type="text/csv")
    response["Content-Disposition"] = f'attachment; filename="{safe_stem}.csv"'
    writer = csv.writer(response)
    writer.writerow(header)
    for row in rows:
        writer.writerow(row)
    return response
