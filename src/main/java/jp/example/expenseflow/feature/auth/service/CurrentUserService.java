package jp.example.expenseflow.feature.auth.service;

import jp.example.expenseflow.feature.auth.domain.AppUser;
import jp.example.expenseflow.feature.auth.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {

    private final AppUserRepository appUserRepository;

    public CurrentUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional(readOnly = true)
    public CurrentUser require(String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("認証ユーザーがDBに存在しません"));
        return new CurrentUser(user, user.getDepartment());
    }
}
