package vn.exampractice.proctoring.internal;

/** Loại sự kiện trong nhật ký giám sát (database.md mục 6). */
enum ProctorEventType {

    /** Trình duyệt báo: học sinh rời tab làm bài. */
    TAB_HIDDEN,

    /** Trình duyệt báo: học sinh quay lại tab làm bài. */
    TAB_VISIBLE,

    /** Trình duyệt báo: học sinh thoát toàn màn hình. */
    FULLSCREEN_EXIT,

    /** Giáo viên cộng giờ. */
    TEACHER_EXTENDED_TIME,

    /** Giáo viên khóa bài vì vi phạm. */
    TEACHER_LOCKED;

    /** Trình duyệt học sinh được phép gửi loại này; các loại khác chỉ server tự ghi. */
    boolean isReportedByBrowser() {
        return this == TAB_HIDDEN || this == TAB_VISIBLE || this == FULLSCREEN_EXIT;
    }

    /** Có tính vào số vi phạm không. Số vi phạm chỉ để giáo viên tham khảo, hệ thống không tự phạt (ADR-0012). */
    boolean isViolation() {
        return this == TAB_HIDDEN || this == FULLSCREEN_EXIT;
    }
}
