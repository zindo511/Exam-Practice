/**
 * Thời gian còn lại tính theo giờ server: lấy độ lệch giữa giờ server và giờ máy lúc nhận response, nên
 * học sinh chỉnh giờ máy cũng không đổi được. Không bao giờ âm.
 */
export function remainingMs(deadline: string, serverTime: string, receivedAt: number, now: number): number {
    const offset = Date.parse(serverTime) - receivedAt;
    return Math.max(0, Date.parse(deadline) - (now + offset));
}

const pad = (n: number) => String(n).padStart(2, '0');

/** "32:18"; từ 1 giờ trở lên thì "1:05:09". Làm tròn lên để không hiện "00:00" khi vẫn còn một chút. */
export function formatClock(ms: number): string {
    const total = Math.ceil(ms / 1000);
    const hours = Math.floor(total / 3600);
    const minutes = Math.floor((total % 3600) / 60);
    const seconds = total % 60;
    return hours > 0 ? `${hours}:${pad(minutes)}:${pad(seconds)}` : `${pad(minutes)}:${pad(seconds)}`;
}

/** Giờ máy dạng "10:12:05", cho dòng "Đã lưu lúc". */
export function formatTimeOfDay(date: Date): string {
    return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}
