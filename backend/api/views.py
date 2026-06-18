import hashlib
import secrets

from django.db.models import Avg, Count, Max
from rest_framework import status, viewsets
from rest_framework.authentication import BasicAuthentication, SessionAuthentication
from rest_framework.decorators import (
    api_view,
    authentication_classes,
    permission_classes,
)
from rest_framework.permissions import AllowAny, IsAdminUser
from rest_framework.response import Response

from .analytics import aggregate_difficult_words, csv_response
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
    if request.query_params.get("download") == "csv":
        header = [
            "student_id", "name", "age_group", "device_id",
            "sessions_count", "avg_accuracy", "last_session_at",
        ]
        rows = [
            [
                s.id, s.name, s.age_group, s.device_id, s.sessions_count,
                round(s.avg_accuracy, 1) if s.avg_accuracy is not None else "",
                s.last_session_at.isoformat() if s.last_session_at else "",
            ]
            for s in students
        ]
        return csv_response(f"students_{teacher.school}", header, rows)

    data = StudentProgressSerializer(students, many=True).data
    return Response(
        {"school": teacher.school, "student_count": len(data), "students": data}
    )


@api_view(["GET"])
@permission_classes([AllowAny])
def teacher_difficult_words(request):
    """Token-authed: difficult words across the teacher's school, ranked by
    how often learners struggled with them. For the teacher dashboard and
    research. Add ?format=csv to download."""
    teacher = _teacher_from_request(request)
    if teacher is None:
        return Response(
            {"detail": "Authentication required."},
            status=status.HTTP_401_UNAUTHORIZED,
        )
    sessions = Session.objects.filter(student__school__iexact=teacher.school)
    words = aggregate_difficult_words(sessions)
    if request.query_params.get("download") == "csv":
        return csv_response(
            f"difficult_words_{teacher.school}",
            ["word", "occurrences", "student_count"],
            [[w["word"], w["occurrences"], w["student_count"]] for w in words],
        )
    return Response(
        {
            "school": teacher.school,
            "total_sessions": sessions.count(),
            "unique_difficult_words": len(words),
            "words": words,
        }
    )


@api_view(["GET"])
@permission_classes([AllowAny])
def teacher_sessions_export(request):
    """Token-authed: raw session rows (incl. difficult words) for the
    teacher's school, for mining. CSV by default; ?format=json for JSON."""
    teacher = _teacher_from_request(request)
    if teacher is None:
        return Response(
            {"detail": "Authentication required."},
            status=status.HTTP_401_UNAUTHORIZED,
        )
    sessions = (
        Session.objects.filter(student__school__iexact=teacher.school)
        .select_related("student", "story")
        .order_by("-created_at")
    )
    header = [
        "session_id", "student", "age_group", "device_id", "story",
        "accuracy_percent", "duration_seconds", "difficult_words", "created_at",
    ]
    rows = [
        [
            s.id, s.student.name, s.student.age_group, s.student.device_id,
            s.story.title, s.accuracy_percent, s.duration_seconds,
            "; ".join(str(w) for w in (s.difficult_words or [])),
            s.created_at.isoformat(),
        ]
        for s in sessions
    ]
    if request.query_params.get("download") == "csv":
        return csv_response(f"sessions_{teacher.school}", header, rows)
    return Response([dict(zip(header, row)) for row in rows])


# ---------------------------------------------------------------------------
# Cross-school, anonymized research exports (admin-only)
# ---------------------------------------------------------------------------

def _learner_ref(student_id):
    """Stable pseudonym for a student so researchers can link a learner's
    sessions without exposing their identity."""
    digest = hashlib.sha1(f"learner:{student_id}".encode()).hexdigest()
    return "L" + digest[:10]


@api_view(["GET"])
@authentication_classes([SessionAuthentication, BasicAuthentication])
@permission_classes([IsAdminUser])
def research_difficult_words(request):
    """Admin-only: difficult words ranked across ALL schools. ?download=csv."""
    sessions = Session.objects.all()
    words = aggregate_difficult_words(sessions)
    if request.query_params.get("download") == "csv":
        return csv_response(
            "research_difficult_words",
            ["word", "occurrences", "student_count"],
            [[w["word"], w["occurrences"], w["student_count"]] for w in words],
        )
    return Response(
        {
            "scope": "all_schools",
            "total_sessions": sessions.count(),
            "unique_difficult_words": len(words),
            "words": words,
        }
    )


@api_view(["GET"])
@authentication_classes([SessionAuthentication, BasicAuthentication])
@permission_classes([IsAdminUser])
def research_sessions(request):
    """Admin-only: anonymized session rows across ALL schools, for research.
    No names or device ids — a stable learner_ref links a learner's sessions.
    JSON by default; ?download=csv to export."""
    sessions = (
        Session.objects.select_related("student", "story").order_by("-created_at")
    )
    header = [
        "session_id", "learner_ref", "school", "age_group", "story",
        "difficulty_level", "accuracy_percent", "duration_seconds",
        "difficult_words", "created_at",
    ]
    rows = [
        [
            s.id, _learner_ref(s.student_id), s.student.school, s.student.age_group,
            s.story.title, s.story.difficulty_level, s.accuracy_percent,
            s.duration_seconds,
            "; ".join(str(w) for w in (s.difficult_words or [])),
            s.created_at.isoformat(),
        ]
        for s in sessions
    ]
    if request.query_params.get("download") == "csv":
        return csv_response("research_sessions", header, rows)
    return Response([dict(zip(header, row)) for row in rows])
