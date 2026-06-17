import secrets

from rest_framework import serializers

from .models import Session, Story, Student, Teacher


class TeacherSerializer(serializers.ModelSerializer):
    """Public-safe teacher representation (never exposes password/token)."""

    class Meta:
        model = Teacher
        fields = ["id", "name", "email", "school", "created_at"]
        read_only_fields = fields


class TeacherRegisterSerializer(serializers.ModelSerializer):
    password = serializers.CharField(write_only=True, min_length=6)

    class Meta:
        model = Teacher
        fields = ["id", "name", "email", "school", "password"]

    def validate_email(self, value):
        if Teacher.objects.filter(email__iexact=value).exists():
            raise serializers.ValidationError("A teacher with this email already exists.")
        return value.lower()

    def create(self, validated_data):
        raw_password = validated_data.pop("password")
        teacher = Teacher(**validated_data)
        teacher.set_password(raw_password)
        teacher.auth_token = secrets.token_hex(20)
        teacher.save()
        return teacher


class TeacherLoginSerializer(serializers.Serializer):
    email = serializers.EmailField()
    password = serializers.CharField(write_only=True)


class StudentProgressSerializer(serializers.ModelSerializer):
    """Student plus aggregated progress, for the teacher dashboard."""

    sessions_count = serializers.IntegerField(read_only=True)
    avg_accuracy = serializers.SerializerMethodField()
    last_session_at = serializers.DateTimeField(read_only=True)

    class Meta:
        model = Student
        fields = [
            "id", "name", "age_group", "device_id",
            "sessions_count", "avg_accuracy", "last_session_at",
        ]

    def get_avg_accuracy(self, obj):
        return round(obj.avg_accuracy, 1) if obj.avg_accuracy is not None else None


class StudentSerializer(serializers.ModelSerializer):
    # On a returning login the frontend only sends name + device_id, so
    # school/age_group must be optional. They're supplied on first sign-up.
    school = serializers.CharField(required=False, allow_blank=True, default="")
    age_group = serializers.CharField(required=False, allow_blank=True, default="")

    class Meta:
        model = Student
        fields = [
            "id",
            "name",
            "school",
            "age_group",
            "device_id",
            "created_at",
            "updated_at",
        ]
        read_only_fields = ["id", "created_at", "updated_at"]
        # The model's (name, device_id) UniqueConstraint would otherwise add a
        # UniqueTogetherValidator that rejects a returning student before our
        # get_or_create login logic runs. The DB constraint still protects us.
        validators = []


class StorySerializer(serializers.ModelSerializer):
    class Meta:
        model = Story
        fields = [
            "id",
            "title",
            "age_group",
            "content",
            "life_skill",
            "life_skill_lesson",
            "created_at",
            "updated_at",
        ]
        read_only_fields = ["id", "created_at", "updated_at"]


class SessionSerializer(serializers.ModelSerializer):
    class Meta:
        model = Session
        fields = [
            "id",
            "student",
            "story",
            "duration_seconds",
            "accuracy_percent",
            "difficult_words",
            "created_at",
        ]
        read_only_fields = ["id", "created_at"]
