from django.urls import path
from rest_framework.routers import DefaultRouter

from .views import (
    SessionViewSet,
    StoryViewSet,
    StudentViewSet,
    school_autocomplete,
    teacher_difficult_words,
    teacher_login,
    teacher_register,
    teacher_sessions_export,
    teacher_students,
)

router = DefaultRouter()
router.register(r"students", StudentViewSet, basename="student")
router.register(r"stories", StoryViewSet, basename="story")
router.register(r"sessions", SessionViewSet, basename="session")

urlpatterns = [
    path("teachers/register/", teacher_register, name="teacher-register"),
    path("teachers/login/", teacher_login, name="teacher-login"),
    path("teachers/students/", teacher_students, name="teacher-students"),
    path("teachers/difficult-words/", teacher_difficult_words, name="teacher-difficult-words"),
    path("teachers/sessions/", teacher_sessions_export, name="teacher-sessions-export"),
    path("schools/", school_autocomplete, name="school-autocomplete"),
] + router.urls
