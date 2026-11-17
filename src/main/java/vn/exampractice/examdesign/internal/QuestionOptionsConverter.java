package vn.exampractice.examdesign.internal;

import java.util.List;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Chuyển danh sách lựa chọn của câu hỏi sang chuỗi JSON trong cột {@code options_json} và ngược
 * lại (database.md mục 4), ví dụ {@code ["Hà Nội","Huế","Đà Nẵng"]}.
 *
 * <p>Dùng một {@link JsonMapper} riêng, không lấy bean của Spring: định dạng lưu trong database
 * không được đổi theo cấu hình JSON của API.
 */
@Converter
class QuestionOptionsConverter implements AttributeConverter<List<String>, String> {

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
