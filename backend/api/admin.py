import json

from django.contrib import admin
from django.db.models import Avg, Count
from django.utils.html import format_html

from .models import Student, Story, Session


def _badge(label, bg, fg="white"):
    return format_html(
        '<span style="color: {}; background-color: {}; padding: 4px 8px; '
        'border-radius: 4px; font-weight: bold; white-space: nowrap;">{}</span>',
        fg, bg, label,
    )


def _word_count(content):
    """Stories store content as a JSON-stringified list of sentences."""
    try:
        parts = json.loads(content)
        text = " ".join(str(p) for p in parts) if isinstance(parts, list) else str(content)
    except (ValueError, TypeError):
        text = content or ""
    return len(text.split())


@admin.register(Student)
class StudentAdmin(admin.ModelAdmin):
    list_display = (
        'id', 'name', 'school', 'age_group',
        'session_count', 'avg_accuracy', 'status_badge', 'created_at',
    )
    list_filter = ('age_group', 'school')
    search_fields = ('name', 'school', 'device_id')
    ordering = ('-created_at',)

    def get_queryset(self, request):
        # Annotate once to avoid an N+1 query per row.
        return super().get_queryset(request).annotate(
            _session_count=Count('sessions'),
            _avg_accuracy=Avg('sessions__accuracy_percent'),
        )

    @admin.display(description='Sessions', ordering='_session_count')
    def session_count(self, obj):
        return obj._session_count

    @admin.display(description='Avg accuracy', ordering='_avg_accuracy')
    def avg_accuracy(self, obj):
        if obj._avg_accuracy is None:
            return '—'
        return f'{obj._avg_accuracy:.1f}%'

    @admin.display(description='Status')
    def status_badge(self, obj):
        # Mirrors the old admin's "Active Triggers".
        if obj._session_count == 0:
            return _badge('🆕 First Time', '#6c757d')
        avg = obj._avg_accuracy or 0
        if avg >= 80:
            return _badge('🌟 High Performer', '#28a745')
        if avg < 50:
            return _badge('⚠️ Needs Support', '#dc3545')
        if obj._session_count > 10:
            return _badge('🔥 Regular Learner', '#6f42c1')
        return _badge('👍 On Track', '#ffc107', fg='black')


@admin.register(Story)
class StoryAdmin(admin.ModelAdmin):
    list_display = ('id', 'title', 'age_group', 'life_skill', 'word_count', 'times_read', 'created_at')
    list_filter = ('age_group', 'life_skill')
    search_fields = ('title', 'life_skill')
    ordering = ('-created_at',)

    def get_queryset(self, request):
        return super().get_queryset(request).annotate(_times_read=Count('sessions'))

    @admin.display(description='Words')
    def word_count(self, obj):
        return _word_count(obj.content)

    @admin.display(description='Times read', ordering='_times_read')
    def times_read(self, obj):
        return obj._times_read


@admin.register(Session)
class SessionAdmin(admin.ModelAdmin):
    list_display = (
        'id', 'student', 'story', 'accuracy_percent', 'performance_badge',
        'duration_display', 'difficult_word_count', 'created_at',
    )
    list_filter = ('story', 'student__age_group')
    search_fields = ('student__name', 'story__title')
    readonly_fields = ('difficult_words',)
    ordering = ('-created_at',)
    list_select_related = ('student', 'story')

    @admin.display(description='Performance')
    def performance_badge(self, obj):
        if obj.accuracy_percent >= 80:
            return _badge('High Performer', '#28a745')
        elif obj.accuracy_percent >= 50:
            return _badge('Needs Practice', '#ffc107', fg='black')
        else:
            return _badge('Requires Support', '#dc3545')

    @admin.display(description='Duration', ordering='duration_seconds')
    def duration_display(self, obj):
        m, s = divmod(int(obj.duration_seconds or 0), 60)
        return f'{m}m {s:02d}s'

    @admin.display(description='Difficult words')
    def difficult_word_count(self, obj):
        words = obj.difficult_words
        return len(words) if isinstance(words, list) else 0
