from django.contrib import admin
from django.utils.html import format_html

from .models import Session, Story, Student


# 1. Student Dashboard
@admin.register(Student)
class StudentAdmin(admin.ModelAdmin):
    list_display = ('id', 'name', 'school', 'age_group', 'created_at')
    list_filter = ('age_group', 'school')
    search_fields = ('name', 'school', 'device_id')
    ordering = ('-created_at',)


# 2. Story Dashboard
@admin.register(Story)
class StoryAdmin(admin.ModelAdmin):
    list_display = ('id', 'title', 'age_group', 'life_skill')
    list_filter = ('age_group', 'life_skill')
    search_fields = ('title', 'life_skill')


# 3. Game Session Dashboard (The Analytics View)
@admin.register(Session)
class SessionAdmin(admin.ModelAdmin):
    list_display = ('id', 'student', 'story', 'accuracy_percent', 'result_badge', 'duration_seconds', 'created_at')
    list_filter = ('story', 'student__age_group')
    search_fields = ('student__name', 'story__title')
    readonly_fields = ('difficult_words',)  # Prevent accidental edits to the raw JSON array
    ordering = ('-created_at',)

    @admin.display(description='Result')
    def result_badge(self, obj):
        passed = obj.accuracy_percent >= 80
        color = '#16a34a' if passed else '#dc2626'
        label = 'PASS' if passed else 'FAIL'
        return format_html(
            '<b style="color:white;background:{};padding:2px 8px;border-radius:6px;">{}</b>',
            color, label,
        )
