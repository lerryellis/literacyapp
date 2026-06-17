from rest_framework import status, viewsets
from rest_framework.response import Response

from .models import Session, Story, Student
from .serializers import SessionSerializer, StorySerializer, StudentSerializer


class StudentViewSet(viewsets.ModelViewSet):
    """CRUD for students, with a login-friendly create.

    The frontend sends a single payload with the child's name and the
    tablet's device_id. Because (name, device_id) is unique together, we
    treat create as "log in or sign up":
      - If that student already exists on this device, return them (200 OK).
      - Otherwise create a new profile (201 Created).
    """

    queryset = Student.objects.all()
    serializer_class = StudentSerializer

    def create(self, request, *args, **kwargs):
        serializer = self.get_serializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        student, created = Student.objects.get_or_create(
            name=data["name"],
            device_id=data["device_id"],
            defaults={
                "school": data.get("school", ""),
                "age_group": data.get("age_group", ""),
            },
        )

        out = self.get_serializer(student)
        http_status = status.HTTP_201_CREATED if created else status.HTTP_200_OK
        return Response(out.data, status=http_status)


class StoryViewSet(viewsets.ModelViewSet):
    """CRUD for stories, optionally filtered by ?age_group=."""

    serializer_class = StorySerializer

    def get_queryset(self):
        queryset = Story.objects.all()
        age_group = self.request.query_params.get("age_group")
        if age_group:
            queryset = queryset.filter(age_group=age_group)
        return queryset


class SessionViewSet(viewsets.ModelViewSet):
    """CRUD for reading sessions (scores and analytics)."""

    queryset = Session.objects.all()
    serializer_class = SessionSerializer
