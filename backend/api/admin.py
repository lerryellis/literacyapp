from django.contrib import admin
from django.utils.html import format_html

from .models import Student, Story, Session


@admin.register(Student)
class StudentAdmin(admin.ModelAdmin):
    list_display = ('id', 'name', 'school', 'age_group', 'created_at')
    list_filter = ('age_group', 'school')
    search_fields = ('name', 'school', 'device_id')
    ordering = ('-created_at',)


@admin.register(Story)
class StoryAdmin(admin.ModelAdmin):
    list_display = ('id', 'title', 'age_group', 'life_skill')
    list_filter = ('age_group', 'life_skill')
    search_fields = ('title', 'life_skill')


@admin.register(Session)
class SessionAdmin(admin.ModelAdmin):
    # Notice we added 'performance_badge' here
    list_display = ('id', 'student', 'story', 'accuracy_percent', 'performance_badge', 'duration_seconds', 'created_at')
    list_filter = ('story', 'student__age_group')
    search_fields = ('student__name', 'story__title')
    readonly_fields = ('difficult_words',)
    ordering = ('-created_at',)

    # Custom HTML column for visual monitoring
    @admin.display(description='Performance')
    def performance_badge(self, obj):
        if obj.accuracy_percent >= 80:
            return format_html('<span style="color: white; background-color: #28a745; padding: 4px 8px; border-radius: 4px; font-weight: bold;">High Performer</span>')
        elif obj.accuracy_percent >= 50:
            return format_html('<span style="color: black; background-color: #ffc107; padding: 4px 8px; border-radius: 4px; font-weight: bold;">Needs Practice</span>')
        else:
            return format_html('<span style="color: white; background-color: #dc3545; padding: 4px 8px; border-radius: 4px; font-weight: bold;">Requires Support</span>')
