from rest_framework import serializers

from .models import Session, Story, Student


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
