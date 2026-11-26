import type { ThemeConfig } from 'antd';

/** Màu và font theo bản phác thảo: tím mực cho thao tác chính, cam cho cảnh báo và thao tác khó đảo ngược. */
export const theme: ThemeConfig = {
    token: {
        colorPrimary: '#43359A',
        colorLink: '#43359A',
        colorError: '#A84A10',
        colorWarning: '#A84A10',
        colorErrorBg: '#FFF0DF',
        colorErrorBorder: '#F0C79A',
        colorWarningBg: '#FFF0DF',
        colorWarningBorder: '#F0C79A',
        colorText: '#1C1B33',
        colorTextSecondary: '#55566F',
        colorTextDescription: '#55566F',
        colorBgLayout: '#F5F6FA',
        colorBorder: '#8F93AB',
        colorBorderSecondary: '#DDE0EA',
        fontFamily: "'Be Vietnam Pro', system-ui, sans-serif",
        fontSize: 16,
        borderRadius: 8,
        controlHeight: 44,
        controlHeightLG: 52,
        fontSizeHeading1: 30,
    },
    components: {
        Layout: { headerBg: '#FFFFFF', siderBg: '#FFFFFF' },
        Menu: { itemSelectedBg: '#ECEAF8' },
    },
};
