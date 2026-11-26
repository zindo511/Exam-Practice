import { Typography } from 'antd';

/** Chỗ giữ của màn chưa làm; làm xong màn cuối cùng thì xóa file này. */
export function NotReadyPage({ title }: { title: string }) {
    return (
        <>
            <Typography.Title level={1}>{title}</Typography.Title>
            <Typography.Paragraph type="secondary">Màn này chưa làm.</Typography.Paragraph>
        </>
    );
}
