import json

from django import forms
from django.contrib import admin
from django.db.models import Avg, Count, Max
from django.utils.html import escape, format_html, format_html_join
from django.utils.safestring import mark_safe

from .analytics import aggregate_difficult_words, csv_response
from .models import Student, Story, Session, Teacher


@admin.action(description="Export selected sessions to CSV")
def export_sessions_csv(modeladmin, request, queryset):
    queryset = queryset.select_related("student", "story")
    header = [
        "session_id", "student", "school", "age_group", "story",
        "accuracy_percent", "duration_seconds", "difficult_words", "created_at",
    ]
    rows = [
        [
            s.id, s.student.name, s.student.school, s.student.age_group,
            s.story.title, s.accuracy_percent, s.duration_seconds,
            "; ".join(str(w) for w in (s.difficult_words or [])),
            s.created_at.isoformat(),
        ]
        for s in queryset
    ]
    return csv_response("sessions_export", header, rows)


@admin.action(description="Export difficult-words report (CSV)")
def export_difficult_words_csv(modeladmin, request, queryset):
    words = aggregate_difficult_words(queryset)
    return csv_response(
        "difficult_words_report",
        ["word", "occurrences", "student_count"],
        [[w["word"], w["occurrences"], w["student_count"]] for w in words],
    )


@admin.action(description="Export selected stories to CSV")
def export_stories_csv(modeladmin, request, queryset):
    header = ["id", "title", "age_group", "difficulty_level", "life_skill",
              "word_count", "content", "created_at"]
    rows = [
        [s.id, s.title, s.age_group, s.difficulty_level, s.life_skill,
         _word_count(s.content), s.content, s.created_at.isoformat()]
        for s in queryset
    ]
    return csv_response("stories_export", header, rows)


@admin.action(description="Export selected teachers to CSV")
def export_teachers_csv(modeladmin, request, queryset):
    # Never export password hashes or auth tokens.
    header = ["id", "name", "email", "school", "created_at"]
    rows = [[t.id, t.name, t.email, t.school, t.created_at.isoformat()] for t in queryset]
    return csv_response("teachers_export", header, rows)


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


def _progress_svg(points):
    """Render an inline SVG line chart of accuracy over time.

    `points` is a list of (accuracy, label) in chronological order.
    Server-side SVG means it always renders — no JS/CDN dependency.
    """
    if not points:
        return mark_safe(
            '<div style="color:#888;padding:12px;">No sessions yet — '
            'the progress chart appears once this student starts reading.</div>'
        )

    W, H = 640, 280
    pad_l, pad_r, pad_t, pad_b = 44, 16, 16, 46
    plot_w, plot_h = W - pad_l - pad_r, H - pad_t - pad_b
    n = len(points)

    def px(i):
        return pad_l + (plot_w * (i / (n - 1)) if n > 1 else plot_w / 2)

    def py(acc):
        return pad_t + plot_h * (1 - (acc or 0) / 100)

    parts = [
        f'<svg viewBox="0 0 {W} {H}" style="max-width:100%;height:auto;'
        'font-family:sans-serif;border:1px solid #eee;border-radius:8px;background:#fff;">'
    ]
    for val in (0, 25, 50, 75, 100):
        gy = py(val)
        parts.append(
            f'<line x1="{pad_l}" y1="{gy:.1f}" x2="{W - pad_r}" y2="{gy:.1f}" '
            'stroke="#ececec" stroke-width="1"/>'
        )
        parts.append(
            f'<text x="{pad_l - 6}" y="{gy + 4:.1f}" text-anchor="end" '
            f'font-size="11" fill="#999">{val}%</text>'
        )
    if n > 1:
        line = " ".join(f"{px(i):.1f},{py(a):.1f}" for i, (a, _) in enumerate(points))
        parts.append(
            f'<polyline fill="none" stroke="#6f42c1" stroke-width="2.5" points="{line}"/>'
        )
    step = max(1, n // 6)
    for i, (acc, label) in enumerate(points):
        cx, cy = px(i), py(acc)
        color = '#28a745' if acc >= 80 else ('#ffc107' if acc >= 50 else '#dc3545')
        parts.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="4" fill="{color}"/>')
        if n <= 8 or i in (0, n - 1) or i % step == 0:
            parts.append(
                f'<text x="{cx:.1f}" y="{H - pad_b + 16:.1f}" text-anchor="middle" '
                f'font-size="10" fill="#666">{escape(label)}</text>'
            )
    parts.append(
        f'<line x1="{pad_l}" y1="{pad_t}" x2="{pad_l}" y2="{H - pad_b}" stroke="#333" stroke-width="1"/>'
    )
    parts.append(
        f'<line x1="{pad_l}" y1="{H - pad_b}" x2="{W - pad_r}" y2="{H - pad_b}" stroke="#333" stroke-width="1"/>'
    )
    parts.append('</svg>')
    return mark_safe("".join(parts))


@admin.register(Teacher)
class TeacherAdmin(admin.ModelAdmin):
    list_display = ('id', 'name', 'email', 'school', 'student_count', 'created_at')
    list_filter = ('school',)
    search_fields = ('name', 'email', 'school')
    # Hashed password / token are never edited by hand here.
    readonly_fields = ('password', 'auth_token', 'created_at', 'updated_at')
    ordering = ('-created_at',)
    actions = [export_teachers_csv]

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
    actions = ['export_students_csv']
    readonly_fields = ('profile_card', 'progress_chart', 'created_at', 'updated_at')
    fieldsets = (
        ('Profile', {'fields': ('profile_card',)}),
        ('Details', {'fields': ('name', 'school', 'age_group', 'device_id')}),
        ('Progress over time', {'fields': ('progress_chart',)}),
    )

    @admin.display(description='')
    def profile_card(self, obj):
        if not obj or not obj.pk:
            return '—'
        agg = obj.sessions.aggregate(
            n=Count('id'), avg=Avg('accuracy_percent'), last=Max('created_at')
        )
        n = agg['n'] or 0
        avg = agg['avg']
        avg_txt = f'{avg:.1f}%' if avg is not None else '—'
        last_txt = agg['last'].strftime('%b %d, %Y') if agg['last'] else 'never'
        if n == 0:
            label, color, fg = '🆕 First Time', '#6c757d', 'white'
        elif (avg or 0) >= 80:
            label, color, fg = '🌟 High Performer', '#28a745', 'white'
        elif (avg or 0) < 50:
            label, color, fg = '⚠️ Needs Support', '#dc3545', 'white'
        elif n > 10:
            label, color, fg = '🔥 Regular Learner', '#6f42c1', 'white'
        else:
            label, color, fg = '👍 On Track', '#ffc107', 'black'
        return format_html(
            '<div style="display:flex;gap:18px;align-items:center;border:1px solid #eee;'
            'border-radius:12px;padding:18px;max-width:680px;background:#fff;">'
            '<div style="width:64px;height:64px;border-radius:50%;background:#6f42c1;color:#fff;'
            'display:flex;align-items:center;justify-content:center;font-size:28px;font-weight:bold;">{}</div>'
            '<div style="flex:1;min-width:140px;">'
            '<div style="font-size:20px;font-weight:bold;">{}</div>'
            '<div style="color:#666;">{} &middot; Age {}</div>'
            '<div style="color:#999;font-size:12px;font-family:monospace;">{}</div></div>'
            '<div style="text-align:center;"><div style="font-size:22px;font-weight:bold;">{}</div>'
            '<div style="color:#888;font-size:12px;">sessions</div></div>'
            '<div style="text-align:center;"><div style="font-size:22px;font-weight:bold;">{}</div>'
            '<div style="color:#888;font-size:12px;">avg accuracy</div></div>'
            '<div style="text-align:center;"><div style="font-size:13px;color:#444;">last read</div>'
            '<div style="color:#888;font-size:12px;">{}</div></div>'
            '<div><span style="color:{};background:{};padding:5px 10px;border-radius:6px;'
            'font-weight:bold;white-space:nowrap;">{}</span></div></div>',
            (obj.name[:1] or '?').upper(), obj.name, obj.school, obj.age_group,
            obj.device_id, n, avg_txt, last_txt, fg, color, label,
        )

    @admin.display(description='Accuracy per session (oldest → newest)')
    def progress_chart(self, obj):
        if not obj or not obj.pk:
            return '—'
        rows = obj.sessions.order_by('created_at').values('accuracy_percent', 'created_at')
        points = [(r['accuracy_percent'], r['created_at'].strftime('%m/%d')) for r in rows]
        return _progress_svg(points)

    @admin.action(description='Export selected students to CSV')
    def export_students_csv(self, request, queryset):
        queryset = queryset.annotate(
            _n=Count('sessions'),
            _avg=Avg('sessions__accuracy_percent'),
        )
        header = ['student_id', 'name', 'school', 'age_group', 'device_id',
                  'sessions_count', 'avg_accuracy', 'created_at']
        rows = [
            [s.id, s.name, s.school, s.age_group, s.device_id, s._n,
             round(s._avg, 1) if s._avg is not None else '', s.created_at.isoformat()]
            for s in queryset
        ]
        return csv_response('students_export', header, rows)

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
    actions = [export_stories_csv]
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
    readonly_fields = ('difficult_words', 'difficult_words_detail')
    ordering = ('-created_at',)
    list_select_related = ('student', 'story')
    actions = [export_sessions_csv, export_difficult_words_csv]

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

    @admin.display(description='Difficult words (detail)')
    def difficult_words_detail(self, obj):
        words = obj.difficult_words if isinstance(obj.difficult_words, list) else []
        if not words:
            return '—'
        chips = format_html_join(
            ' ',
            '<span style="background:#fde2e2; color:#b42318; padding:2px 8px; '
            'border-radius:10px; margin:2px; display:inline-block;">{}</span>',
            ((str(w),) for w in words),
        )
        return format_html('{}', chips)
