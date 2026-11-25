package vn.exampractice.identity.internal;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.exampractice.identity.IdentityApi;
import vn.exampractice.identity.dto.UserSummary;

/**
 * Cài đặt {@link IdentityApi}: đọc {@link UserAccount} rồi chuyển sang DTO công khai. Chỉ đọc.
 */
@Service
@Transactional(readOnly = true)
class IdentityApiImpl implements IdentityApi {

    private final UserAccountRepository repository;

    IdentityApiImpl(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public Map<Long, UserSummary> findUsers(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserSummary> users = new HashMap<>();
        for (UserAccount account : repository.findAllById(ids)) {
            users.put(account.getId(),
                    new UserSummary(account.getId(), account.getUsername(), account.getFullName()));
        }
        return users;
    }
}
