package jp.example.expenseflow.feature.auth.service;

import jp.example.expenseflow.feature.auth.domain.AppUser;
import jp.example.expenseflow.feature.auth.domain.Department;
import jp.example.expenseflow.feature.auth.domain.UserRole;

public record CurrentUser(AppUser user, Department department) {

    public Long id() {
        return user.getId();
    }

    public String username() {
        return user.getUsername();
    }

    public String displayName() {
        return user.getDisplayName();
    }

    public Long departmentId() {
        return department.getId();
    }

    public UserRole role() {
        return user.getRole();
    }

    public boolean isApprover() {
        return role() == UserRole.APPROVER;
    }
}
