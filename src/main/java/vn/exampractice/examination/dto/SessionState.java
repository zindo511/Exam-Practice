package vn.exampractice.examination.dto;

/** Trạng thái kỳ thi, nhìn từ bên ngoài examination (state-machines.md mục 1). */
public enum SessionState {
    SCHEDULED,
    OPEN,
    CLOSED,
    CANCELLED
}
