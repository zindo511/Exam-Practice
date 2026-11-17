package vn.exampractice.examdesign.internal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test cho luật trong entity {@link Exam}: thông tin đề, thay danh sách câu, câu trùng, lưu
 * trữ. Không cần Spring hay database.
 */
class ExamTest {

    private static final Long TEACHER = 7L;
    private static final Instant NOW = Instant.parse("2026-11-17T08:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(60);

    @Test
    void create_keeps_info_trims_title_and_starts_empty() {
        Exam exam = Exam.create(TEACHER, "  Kiểm tra 15 phút  ", 15, NOW);

        assertThat(exam.getTitle()).isEqualTo("Kiểm tra 15 phút");
        assertThat(exam.getDurationMinutes()).isEqualTo(15);
        assertThat(exam.getCreatedAt()).isEqualTo(NOW);
        assertThat(exam.getUpdatedAt()).isEqualTo(NOW);
        assertThat(exam.questionCount()).isZero();
        assertThat(exam.isArchived()).isFalse();
        assertThat(exam.isOwnedBy(TEACHER)).isTrue();
        assertThat(exam.isOwnedBy(8L)).isFalse();
    }

    @Test
    void title_and_duration_must_be_valid() {
        assertThatThrownBy(() -> Exam.create(TEACHER, " ", 15, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Exam.create(TEACHER, "x".repeat(Exam.MAX_TITLE_LENGTH + 1), 15, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Exam.create(TEACHER, "Đề", 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Exam.create(TEACHER, "Đề", Exam.MAX_DURATION_MINUTES + 1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_changes_info_and_keeps_created_at() {
        Exam exam = Exam.create(TEACHER, "Đề cũ", 15, NOW);

        exam.update("Đề mới", 45, LATER);

        assertThat(exam.getTitle()).isEqualTo("Đề mới");
        assertThat(exam.getDurationMinutes()).isEqualTo(45);
        assertThat(exam.getCreatedAt()).isEqualTo(NOW);
        assertThat(exam.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void replace_questions_numbers_positions_from_1_in_given_order() {
        Exam exam = Exam.create(TEACHER, "Đề", 15, NOW);

        exam.replaceQuestions(List.of(30L, 10L, 20L), LATER);

        assertThat(exam.getQuestions()).extracting(ExamQuestion::getPosition).containsExactly(1, 2, 3);
        assertThat(exam.getQuestions()).extracting(ExamQuestion::getQuestionId).containsExactly(30L, 10L, 20L);
        assertThat(exam.questionCount()).isEqualTo(3);
        assertThat(exam.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void replace_questions_overwrites_previous_list() {
        Exam exam = Exam.create(TEACHER, "Đề", 15, NOW);
        exam.replaceQuestions(List.of(1L, 2L), NOW);

        exam.replaceQuestions(List.of(2L), LATER);

        assertThat(exam.getQuestions()).singleElement().satisfies(question -> {
            assertThat(question.getQuestionId()).isEqualTo(2L);
            assertThat(question.getPosition()).isEqualTo(1);
        });

        exam.replaceQuestions(List.of(), LATER);
        assertThat(exam.questionCount()).isZero();
    }

    @Test
    void duplicate_question_is_rejected_and_exam_stays_unchanged() {
        Exam exam = Exam.create(TEACHER, "Đề", 15, NOW);
        exam.replaceQuestions(List.of(1L), NOW);

        assertThatThrownBy(() -> exam.replaceQuestions(List.of(2L, 2L), LATER))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(exam.getQuestions()).extracting(ExamQuestion::getQuestionId).containsExactly(1L);
        assertThat(exam.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void number_of_questions_is_limited() {
        Exam exam = Exam.create(TEACHER, "Đề", 15, NOW);
        List<Long> tooMany = new ArrayList<>();
        for (long id = 1; id <= Exam.MAX_QUESTIONS + 1; id++) {
            tooMany.add(id);
        }

        assertThatThrownBy(() -> exam.replaceQuestions(tooMany, LATER))
                .isInstanceOf(IllegalArgumentException.class);
        exam.replaceQuestions(tooMany.subList(0, Exam.MAX_QUESTIONS), LATER);
        assertThat(exam.questionCount()).isEqualTo(Exam.MAX_QUESTIONS);
    }

    @Test
    void questions_cannot_be_changed_from_outside() {
        Exam exam = Exam.create(TEACHER, "Đề", 15, NOW);

        assertThatThrownBy(() -> exam.getQuestions().add(new ExamQuestion(1, 1L)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void archived_exam_cannot_be_changed_or_archived_again() {
        Exam exam = Exam.create(TEACHER, "Đề", 15, NOW);

        exam.archive(LATER);

        assertThat(exam.isArchived()).isTrue();
        assertThat(exam.getArchivedAt()).isEqualTo(LATER);
        assertThatThrownBy(() -> exam.update("Đề mới", 30, LATER)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> exam.replaceQuestions(List.of(), LATER)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> exam.archive(LATER)).isInstanceOf(IllegalStateException.class);
    }
}
