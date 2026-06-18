from django import template
from django.db.models import Avg, Count

from api.analytics import aggregate_difficult_words
from api.models import Session, Story, Student, Teacher

register = template.Library()


@register.simple_tag
def dashboard_stats():
    """Aggregated stats for the admin dashboard index."""
    sessions = Session.objects.all()
    total_sessions = sessions.count()
    avg = sessions.aggregate(a=Avg("accuracy_percent"))["a"]

    recent = (
        sessions.select_related("student", "story").order_by("-created_at")[:8]
    )
    top_students = (
        Student.objects.annotate(
            n=Count("sessions"), avg=Avg("sessions__accuracy_percent")
        )
        .filter(n__gt=0)
        .order_by("-avg")[:5]
    )

    return {
        "total_students": Student.objects.count(),
        "total_sessions": total_sessions,
        "total_stories": Story.objects.count(),
        "total_teachers": Teacher.objects.count(),
        "avg_accuracy": round(avg, 1) if avg is not None else None,
        "top_words": aggregate_difficult_words(sessions)[:10],
        "recent_sessions": recent,
        "top_students": top_students,
    }
