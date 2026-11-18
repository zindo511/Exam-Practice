package vn.exampractice.examination.internal;

import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Chuyển danh sách lựa chọn của câu trong kỳ thi sang chuỗi JSON trong cột {@code options_json} và
 * ngược lại (database.md mục 5).
 *
 * <p>Giống converter của examdesign nhưng viết riêng: hai module không dùng chung class nội bộ
 * (ADR-0004), và định dạng lưu của mỗi module do chính nó quyết định.
 */
@Converter
class SessionOptionsConverter implements AttributeConverter<List<String>, String> {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<String> options) {
        return JSON.writeValueAsString(options);
    }

    @Override
    public List<String> convertToEntityAttribute(String json) {
        return List.copyOf(JSON.readValue(json, STRING_LIST));
    }
}
