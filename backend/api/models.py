from django.contrib.auth.hashers import check_password, make_password
from django.db import models


class Teacher(models.Model):
    """A teacher who self-registers with email + password and monitors the
    students belonging to their school."""

    name = models.CharField(max_length=255, blank=True)
    email = models.EmailField(unique=True)
    school = models.CharField(max_length=255)
    password = models.CharField(max_length=255)  # stored hashed
    auth_token = models.CharField(max_length=64, blank=True, db_index=True)

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def set_password(self, raw_password):
        self.password = make_password(raw_password)

    def check_password(self, raw_password):
        return check_password(raw_password, self.password)

    def __str__(self):
        return f"{self.email} ({self.school})"


class Student(models.Model):
    """A child using the literacy app, identified by their device."""

    name = models.CharField(max_length=255)
    school = models.CharField(max_length=255)
    age_group = models.CharField(max_length=50)
    device_id = models.CharField(max_length=255)

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        constraints = [
            # A name is unique per device, so one tablet can host many
            # profiles ("Kofi", "Ama", ...) while the same name on a
            # different device stays a separate student.
            models.UniqueConstraint(
                fields=["name", "device_id"],
                name="unique_student_name_per_device",
            )
        ]

    def __str__(self):
        return f"{self.name} ({self.device_id})"


class Story(models.Model):
    """A reading story tied to a life-skill lesson."""

    title = models.CharField(max_length=255)
    age_group = models.CharField(max_length=50)
    content = models.TextField()
    life_skill = models.CharField(max_length=255)
    life_skill_lesson = models.TextField()
    difficulty_level = models.CharField(max_length=20, default="Beginner", blank=True)

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return self.title


class Session(models.Model):
    """A single reading session of one student working through one story."""

    student = models.ForeignKey(
        Student,
        on_delete=models.CASCADE,
        related_name="sessions",
    )
    story = models.ForeignKey(
        Story,
        on_delete=models.CASCADE,
        related_name="sessions",
    )
    duration_seconds = models.IntegerField()
    accuracy_percent = models.FloatField()
    difficult_words = models.JSONField(default=list, blank=True)

    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"{self.student.name} - {self.story.title}"
