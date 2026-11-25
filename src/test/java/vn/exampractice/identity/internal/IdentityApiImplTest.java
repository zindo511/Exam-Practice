package vn.exampractice.identity.internal;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.exampractice.identity.dto.UserSummary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit test cho {@link IdentityApiImpl#findUsers}. */
@ExtendWith(MockitoExtension.class)
class IdentityApiImplTest {

    @Mock
    private UserAccountRepository repository;

    @InjectMocks
    private IdentityApiImpl api;

    @Test
    void returns_users_by_id_and_leaves_out_unknown_ids() {
        UserAccount alice = account(11L, "alice", "Nguyễn Alice");
        when(repository.findAllById(List.of(11L, 99L))).thenReturn(List.of(alice));

        Map<Long, UserSummary> users = api.findUsers(List.of(11L, 99L));

        assertThat(users).containsExactly(Map.entry(11L, new UserSummary(11L, "alice", "Nguyễn Alice")));
    }

    @Test
    void empty_ids_do_not_query_the_database() {
        assertThat(api.findUsers(List.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    private static UserAccount account(Long id, String username, String fullName) {
        UserAccount account = mock(UserAccount.class);
        when(account.getId()).thenReturn(id);
        when(account.getUsername()).thenReturn(username);
        when(account.getFullName()).thenReturn(fullName);
        return account;
    }
}
