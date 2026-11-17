package vn.exampractice.examdesign.internal;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test cho luật trong entity {@link Question}: số lựa chọn, vị trí đáp án đúng, lưu trữ.
 * Không cần Spring hay database.
 */
class QuestionTest {

    private static final Long TEACHER = 7L;
    private static final Instant NOW = Instant.parse("2026-11-17T08:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(60);
    private static final List<String> OPTIONS = List.of("Hà Nội", "Huế", "Đà Nẵng");

    @Test
    void create_keeps_data_and_trims_text() {
        Question question = Question.create(TEACHER, "  Thủ đô của Việt Nam?  ",
                List.of(" Hà Nội ", "Huế"), 0, NOW);

        assertThat(question.getTeacherId()).isEqualTo(TEACHER);
        assertThat(question.getContent()).isEqualTo("Thủ đô của Việt Nam?");
        assertThat(question.getOptions()).containsExactly("Hà Nội", "Huế");
        assertThat(question.getCorrectOption()).isZero();
        assertThat(question.getCreatedAt()).isEqualTo(NOW);
        assertThat(question.getUpdatedAt()).isEqualTo(NOW);
        assertThat(question.isArchived()).isFalse();
        assertThat(question.isOwnedBy(TEACHER)).isTrue();
        assertThat(question.isOwnedBy(8L)).isFalse();
    }

    @Test
    void options_cannot_be_changed_from_outside() {
        Question question = Question.create(TEACHER, "Câu hỏi", OPTIONS, 0, NOW);

        assertThatThrownBy(() -> question.getOptions().add("Cần Thơ"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void number_of_options_must_be_between_2_and_6() {
        assertThatThrownBy(() -> Question.create(TEACHER, "Câu hỏi", List.of("A"), 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Question.create(TEACHER, "Câu hỏi",
                List.of("A", "B", "C", "D", "E", "F", "G"), 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Question.create(TEACHER, "Câu hỏi", List.of("A", "B", "C", "D", "E", "F"), 5, NOW)
                .getOptions()).hasSize(6);
    }

    @Test
    void correct_option_must_point_to_an_existing_option() {
        assertThatThrownBy(() -> Question.create(TEACHER, "Câu hỏi", OPTIONS, 3, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Question.create(TEACHER, "Câu hỏi", OPTIONS, -1, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void content_and_options_must_not_be_blank() {
        assertThatThrownBy(() -> Question.create(TEACHER, " ", OPTIONS, 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Question.create(TEACHER, "Câu hỏi", List.of("A", " "), 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_replaces_content_and_keeps_created_at() {
        Question question = Question.create(TEACHER, "Câu cũ", OPTIONS, 0, NOW);

        question.update("Câu mới", List.of("Đúng", "Sai"), 1, LATER);

        assertThat(question.getContent()).isEqualTo("Câu mới");
        assertThat(question.getOptions()).containsExactly("Đúng", "Sai");
        assertThat(question.getCorrectOption()).isEqualTo(1);
        assertThat(question.getCreatedAt()).isEqualTo(NOW);
        assertThat(question.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void invalid_update_leaves_question_unchanged() {
        Question question = Question.create(TEACHER, "Câu cũ", OPTIONS, 0, NOW);

        assertThatThrownBy(() -> question.update("Câu mới", List.of("A", "B"), 5, LATER))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(question.getContent()).isEqualTo("Câu cũ");
        assertThat(question.getOptions()).isEqualTo(OPTIONS);
        assertThat(question.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void archived_question_cannot_be_updated_or_archived_again() {
        Question question = Question.create(TEACHER, "Câu hỏi", OPTIONS, 0, NOW);

        question.archive(LATER);

        assertThat(question.isArchived()).isTrue();
        assertThat(question.getArchivedAt()).isEqualTo(LATER);
        assertThatThrownBy(() -> question.update("Câu mới", OPTIONS, 0, LATER))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> question.archive(LATER))
                .isInstanceOf(IllegalStateException.class);
    }
}
