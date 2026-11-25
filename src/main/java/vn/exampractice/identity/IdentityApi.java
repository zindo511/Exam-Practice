package vn.exampractice.identity;

import java.util.Collection;
import java.util.Map;

import vn.exampractice.identity.dto.UserSummary;

/**
 * Cổng gọi đồng bộ cho module khác (ADR-0004). Ví dụ: lấy tên của nhiều user theo danh sách ID,
 * gom ID gọi một lần (ADR-0007).
 */
public interface IdentityApi {

    /**
     * Thông tin của nhiều user trong một truy vấn, theo ID. ID không tồn tại thì không có trong kết quả.
     *
     * @param ids danh sách ID cần lấy; rỗng thì trả map rỗng, không truy vấn
     */
    Map<Long, UserSummary> findUsers(Collection<Long> ids);
}
