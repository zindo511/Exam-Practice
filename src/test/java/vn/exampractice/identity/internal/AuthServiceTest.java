package vn.exampractice.identity.internal;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.exampractice.shared.error.BusinessException;
import vn.exampractice.shared.error.CommonErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Unit test cho {@link AuthService#currentUser}; đăng ký và đăng nhập xem {@link AuthControllerTest}. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserAccountRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService service;

    @Test
    void current_user_has_name_and_role() {
        UserAccount an = UserAccount.register("an", "hash", "Nguyễn An", Role.STUDENT, Instant.now());
        when(repository.findById(9L)).thenReturn(Optional.of(an));

        UserResponse response = service.currentUser(9L);

        assertThat(response.username()).isEqualTo("an");
        assertThat(response.fullName()).isEqualTo("Nguyễn An");
        assertThat(response.role()).isEqualTo(Role.STUDENT);
    }

    @Test
    void missing_account_is_unauthenticated() {
        when(repository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.currentUser(9L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHENTICATED));
    }
}
