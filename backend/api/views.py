import secrets

from django.db.models import Avg, Count, Max
from rest_framework import status, viewsets
from rest_framework.decorators import api_view, permission_classes
from rest_framework.permissions import AllowAny
from rest_framework.response import Response

from .models import Session, Story, Student, Teacher
from .serializers import (
    SessionSerializer,
    StorySerializer,
    StudentProgressSerializer,
    StudentSerializer,
    TeacherLoginSerializer,
    TeacherRegisterSerializer,
    TeacherSerializer,
)


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


# ---------------------------------------------------------------------------
# Teacher onboarding + school-scoped student progress
# ---------------------------------------------------------------------------

def _teacher_from_request(request):
    """Resolve the teacher from an 'Authorization: Token <token>' header
    (or a ?token= fallback for easy testing)."""
    auth = request.headers.get("Authorization", "")
    if auth.lower().startswith("token "):
        token = auth.split(" ", 1)[1].strip()
    else:
        token = request.query_params.get("token", "").strip()
    if not token:
        return None
    return Teacher.objects.filter(auth_token=token).first()


@api_view(["POST"])
@permission_classes([AllowAny])
def teacher_register(request):
    """Teacher self-signup: email + password + school -> account + token."""
    serializer = TeacherRegisterSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    teacher = serializer.save()
    return Response(
        {**TeacherSerializer(teacher).data, "token": teacher.auth_token},
        status=status.HTTP_201_CREATED,
    )


@api_view(["POST"])
@permission_classes([AllowAny])
def teacher_login(request):
    """Teacher login: email + password -> token."""
    serializer = TeacherLoginSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    email = serializer.validated_data["email"].lower()
    teacher = Teacher.objects.filter(email__iexact=email).first()
    if teacher is None or not teacher.check_password(serializer.validated_data["password"]):
        return Response(
            {"detail": "Invalid email or password."},
            status=status.HTTP_401_UNAUTHORIZED,
        )
    if not teacher.auth_token:
        teacher.auth_token = secrets.token_hex(20)
        teacher.save(update_fields=["auth_token"])
    return Response({**TeacherSerializer(teacher).data, "token": teacher.auth_token})


@api_view(["GET"])
@permission_classes([AllowAny])
def school_autocomplete(request):
    """Typeahead for schools already in the system, so teachers pick an
    existing school instead of retyping it. Usage: /api/schools/?q=acc"""
    q = request.query_params.get("q", "").strip()
    student_qs = Student.objects.exclude(school="")
    teacher_qs = Teacher.objects.exclude(school="")
    if q:
        student_qs = student_qs.filter(school__icontains=q)
        teacher_qs = teacher_qs.filter(school__icontains=q)
    schools = set(student_qs.values_list("school", flat=True))
    schools |= set(teacher_qs.values_list("school", flat=True))
    return Response(sorted(schools)[:20])


@api_view(["GET"])
@permission_classes([AllowAny])
def teacher_students(request):
    """Token-authed: students in the teacher's school with aggregated
    progress, for the teacher dashboard."""
    teacher = _teacher_from_request(request)
    if teacher is None:
        return Response(
            {"detail": "Authentication required."},
            status=status.HTTP_401_UNAUTHORIZED,
        )
    students = (
        Student.objects.filter(school__iexact=teacher.school)
        .annotate(
            sessions_count=Count("sessions"),
            avg_accuracy=Avg("sessions__accuracy_percent"),
            last_session_at=Max("sessions__created_at"),
        )
        .order_by("name")
    )
    data = StudentProgressSerializer(students, many=True).data
    return Response(
        {"school": teacher.school, "student_count": len(data), "students": data}
    )
