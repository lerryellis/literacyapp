from rest_framework.routers import DefaultRouter

from .views import SessionViewSet, StoryViewSet, StudentViewSet

router = DefaultRouter()
router.register(r"students", StudentViewSet, basename="student")
router.register(r"stories", StoryViewSet, basename="story")
router.register(r"sessions", SessionViewSet, basename="session")

urlpatterns = router.urls
