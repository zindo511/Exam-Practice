import { Tag } from 'antd';
import type { SessionStatus } from './api';
import { SESSION_STATUS_LABEL } from './format';

/** Màu chỉ để nhìn nhanh; chữ trên nhãn đã đủ nghĩa. */
const COLOR: Record<SessionStatus, string | undefined> = {
    SCHEDULED: 'blue',
    OPEN: 'purple',
    CLOSED: undefined,
    CANCELLED: undefined,
};

export function SessionStatusTag({ status }: { status: SessionStatus }) {
    return <Tag color={COLOR[status]} variant="filled">{SESSION_STATUS_LABEL[status]}</Tag>;
}
