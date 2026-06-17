import json

from django import forms
from django.contrib import admin
from django.db.models import Avg, Count
from django.utils.html import format_html

from .models import Student, Story, Session, Teacher


# Age bands the Android app filters by (?age_group=). Keep these in sync with
# the app so a typo here can't hide a story from learners.
AGE_GROUP_CHOICES = [
    ('5-7', 'Explorers (5-7)'),
    ('8-10', 'Builders (8-10)'),
    ('11-13', 'Navigators (11-13)'),
    ('14-15', 'Leaders (14-15)'),
    ('16-18', 'Changemakers (16-18)'),
]

LIFE_SKILL_CHOICES = [(s, s) for s in [
    'Self-awareness',
    'Critical thinking',
    'Problem solving + Decision making',
    'Communication + Interpersonal',
    'Coping with stress',
]]

DIFFICULTY_CHOICES = [(d, d) for d in ['Beginner', 'Intermediate', 'Advanced']]


class StoryAdminForm(forms.ModelForm):
    """Let admins write a story naturally (one sentence per line) instead of
    hand-typing the JSON array the app consumes.

    The Android app reads `content` as a JSON array of sentences and shows one
    sentence per read-aloud step (see app's Story.kt). This form hides that:
    you type plain lines, we serialize to JSON on save and decode back on edit.
    """

    sentences = forms.CharField(
        label='Story sentences',
        widget=forms.Textarea(attrs={'rows': 12, 'style': 'width: 90%; font-size: 14px;'}),
        help_text=(
            'Write the story with ONE SENTENCE PER LINE. Each line becomes a '
            'single read-aloud step in the app. Blank lines are ignored.'
        ),
    )
    age_group = forms.ChoiceField(choices=AGE_GROUP_CHOICES)
    life_skill = forms.ChoiceField(choices=LIFE_SKILL_CHOICES)
    difficulty_level = forms.ChoiceField(choices=DIFFICULTY_CHOICES, initial='Beginner')

    class Meta:
        model = Story
        # `content` is intentionally excluded — it's derived from `sentences`.
        fields = ['title', 'age_group', 'difficulty_level', 'life_skill', 'life_skill_lesson']

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        instance = getattr(self, 'instance', None)
        # Keep an existing value selectable even if it predates these fixed
        # choices (e.g. legacy seed data), so editing never silently drops it.
        if instance and instance.pk:
            for field_name in ('age_group', 'life_skill', 'difficulty_level'):
                current = getattr(instance, field_name, '')
                field = self.fields[field_name]
                if current and current not in dict(field.choices):
                    field.choices = [(current, f'{current} (existing)')] + list(field.choices)
        # On edit, decode the stored JSON back into one-sentence-per-line text.
        if instance and instance.pk and instance.content:
            try:
                parts = json.loads(instance.content)
                if isinstance(parts, list):
                    self.fields['sentences'].initial = '\n'.join(str(p) for p in parts)
                else:
                    self.fields['sentences'].initial = instance.content
            except (ValueError, TypeError):
                self.fields['sentences'].initial = instance.content

    def save(self, commit=True):
        story = super().save(commit=False)
        lines = [ln.strip() for ln in self.cleaned_data['sentences'].splitlines() if ln.strip()]
        story.content = json.dumps(lines, ensure_ascii=False)
        if commit:
            story.save()
        return story


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


@admin.register(Teacher)
class TeacherAdmin(admin.ModelAdmin):
    list_display = ('id', 'name', 'email', 'school', 'student_count', 'created_at')
    list_filter = ('school',)
    search_fields = ('name', 'email', 'school')
    # Hashed password / token are never edited by hand here.
    readonly_fields = ('password', 'auth_token', 'created_at', 'updated_at')
    ordering = ('-created_at',)

    @admin.display(description='Students in school')
    def student_count(self, obj):
        return Student.objects.filter(school__iexact=obj.school).count()


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
    form = StoryAdminForm
    list_display = ('id', 'title', 'age_group', 'difficulty_level', 'life_skill', 'word_count', 'sentence_count', 'times_read', 'created_at')
    list_filter = ('age_group', 'difficulty_level', 'life_skill')
    search_fields = ('title', 'life_skill')
    ordering = ('-created_at',)
    fieldsets = (
        (None, {'fields': ('title', 'age_group', 'difficulty_level')}),
        ('Story', {'fields': ('sentences',)}),
        ('Life skill', {'fields': ('life_skill', 'life_skill_lesson')}),
    )

    @admin.display(description='Sentences')
    def sentence_count(self, obj):
        try:
            parts = json.loads(obj.content)
            return len(parts) if isinstance(parts, list) else 1
        except (ValueError, TypeError):
            return 1 if obj.content else 0

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
