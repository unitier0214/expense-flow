package jp.example.expenseflow.feature.expense.web;

import java.util.UUID;
import jp.example.expenseflow.feature.expense.service.ExpenseConflictException;
import jp.example.expenseflow.feature.expense.service.ExpenseInputException;
import jp.example.expenseflow.feature.expense.service.ExpenseNotFoundException;
import jp.example.expenseflow.feature.expense.service.ExpenseQueryException;
import jp.example.expenseflow.feature.expense.service.ExpenseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class ExpenseExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ExpenseExceptionHandler.class);

    @ExceptionHandler(ExpenseNotFoundException.class)
    public ModelAndView notFound() {
        return error(HttpStatus.NOT_FOUND, "申請が見つかりません", "指定された申請は表示できません。", "/expenses", null);
    }

    @ExceptionHandler(ExpenseConflictException.class)
    public ModelAndView conflict(ExpenseConflictException exception) {
        return error(HttpStatus.CONFLICT, "申請を更新できません", exception.getMessage(), null, null);
    }

    @ExceptionHandler(ExpenseQueryException.class)
    public ModelAndView badQuery(ExpenseQueryException exception) {
        return error(HttpStatus.BAD_REQUEST, "入力を確認してください", exception.getMessage(), "/expenses", null);
    }

    @ExceptionHandler(ExpenseInputException.class)
    public ModelAndView invalidForm(ExpenseInputException exception) {
        ModelAndView model = new ModelAndView("expenses/form");
        model.setStatus(HttpStatus.BAD_REQUEST);
        model.addObject("form", exception.getForm());
        model.addObject("isEdit", exception.getForm().getId() != null);
        model.addObject("pageTitle", exception.getForm().getId() == null ? "経費申請を作成" : "経費申請を編集");
        model.addObject("submitLabel", exception.getForm().getId() == null ? "下書きを保存" : "変更を保存");
        model.addObject("fieldErrors", exception.getFieldErrors());
        model.addObject("categories", ExpenseService.categoryLabels());
        model.addObject("formAction", exception.getForm().getId() == null
                ? "/expenses" : "/expenses/" + exception.getForm().getId() + "/edit");
        addCurrentUsername(model);
        return model;
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentNotValidException.class})
    public ModelAndView invalidRequest() {
        return error(HttpStatus.BAD_REQUEST, "入力を確認してください", "入力値の形式が不正です。", "/expenses", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView forbidden() {
        return error(HttpStatus.FORBIDDEN, "操作できません", "この操作を行う権限がありません。", "/expenses", null);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class,
            jakarta.persistence.OptimisticLockException.class})
    public ModelAndView optimisticConflict() {
        return error(HttpStatus.CONFLICT, "申請が更新されています",
                "別の操作で申請が更新されました。最新情報を再読み込みしてください。", null, null);
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView unexpected(Exception exception) {
        String correlationId = UUID.randomUUID().toString();
        logger.error("Unexpected error correlationId={}", correlationId, exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "エラーが発生しました",
                "処理に失敗しました。時間をおいて再試行してください。", "/expenses", correlationId);
    }

    private ModelAndView error(HttpStatus status, String heading, String message,
                               String backUrl, String correlationId) {
        ModelAndView model = new ModelAndView("expenses/error");
        model.setStatus(status);
        model.addObject("status", status.value());
        model.addObject("heading", heading);
        model.addObject("message", message);
        model.addObject("backUrl", backUrl);
        model.addObject("correlationId", correlationId);
        addCurrentUsername(model);
        return model;
    }

    private void addCurrentUsername(ModelAndView model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            model.addObject("currentUsername", authentication.getName());
        }
    }
}
