from django.contrib import admin

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
    list_display = ('id', 'student', 'story', 'accuracy_percent', 'duration_seconds', 'created_at')
    list_filter = ('story', 'student__age_group')
    search_fields = ('student__name', 'story__title')
    readonly_fields = ('difficult_words',)  # Prevent accidental edits to the raw JSON array
    ordering = ('-created_at',)
