package vn.exampractice.proctoring.internal;

/**
 * Loại sự kiện trình duyệt học sinh được gửi. Tách khỏi {@link ProctorEventType} để học sinh không gửi được
 * các loại dành cho thao tác của giáo viên: giá trị lạ bị trả 400 ngay khi đọc body.
 */
enum BrowserEventType {

    /** Rời tab làm bài. */
    TAB_HIDDEN(ProctorEventType.TAB_HIDDEN),

    /** Quay lại tab làm bài. */
    TAB_VISIBLE(ProctorEventType.TAB_VISIBLE),

    /** Thoát toàn màn hình. */
    FULLSCREEN_EXIT(ProctorEventType.FULLSCREEN_EXIT);

    private final ProctorEventType eventType;

    BrowserEventType(ProctorEventType eventType) {
        this.eventType = eventType;
    }

    /** Loại tương ứng được lưu trong nhật ký. */
    ProctorEventType toEventType() {
        return eventType;
    }
}
