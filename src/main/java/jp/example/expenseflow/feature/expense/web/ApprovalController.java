package jp.example.expenseflow.feature.expense.web;

import java.util.LinkedHashMap;
import java.util.Map;
import jp.example.expenseflow.feature.expense.service.ApprovalInputException;
import jp.example.expenseflow.feature.expense.service.ExpenseService;
import jp.example.expenseflow.feature.expense.service.dto.ApprovalForm;
import jp.example.expenseflow.feature.expense.service.dto.ApprovalListPage;
import jp.example.expenseflow.feature.expense.service.dto.ExpenseDetailView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class ApprovalController {

    private final ExpenseService expenseService;

    public ApprovalController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping("/approvals")
    public ModelAndView list(Authentication authentication,
                             @RequestParam(defaultValue = "0") int page) {
        ApprovalListPage approvalPage = expenseService.findApprovals(
                authentication.getName(), page);
        ModelAndView model = new ModelAndView("approvals/index");
        model.addObject("currentUsername", authentication.getName());
        model.addObject("currentIsApprover", true);
        model.addObject("page", approvalPage);
        return model;
    }

    @PostMapping("/expenses/{id}/approve")
    public ModelAndView approve(Authentication authentication, @PathVariable Long id,
                                @ModelAttribute("approvalForm") ApprovalForm form,
                                BindingResult bindingResult) {
        String username = authentication.getName();
        expenseService.authorizeApprovalTarget(username, id);
        if (bindingResult.hasErrors()) {
            return approvalError(username, id, form, bindingErrors(bindingResult));
        }
        if (form.getVersion() == null) {
            return approvalError(username, id, form,
                    Map.of("version", "versionは必須です"));
        }
        try {
            expenseService.approve(username, id, form);
            return seeOther("/expenses/" + id);
        } catch (ApprovalInputException exception) {
            return approvalError(username, id, exception.getForm(), exception.getFieldErrors());
        }
    }

    @PostMapping("/expenses/{id}/return")
    public ModelAndView returnToApplicant(Authentication authentication, @PathVariable Long id,
                                          @ModelAttribute("approvalForm") ApprovalForm form,
                                          BindingResult bindingResult) {
        String username = authentication.getName();
        expenseService.authorizeApprovalTarget(username, id);
        if (bindingResult.hasErrors()) {
            return approvalError(username, id, form, bindingErrors(bindingResult));
        }
        if (form.getVersion() == null) {
            return approvalError(username, id, form,
                    Map.of("version", "versionは必須です"));
        }
        try {
            expenseService.returnToApplicant(username, id, form);
            return seeOther("/expenses/" + id);
        } catch (ApprovalInputException exception) {
            return approvalError(username, id, exception.getForm(), exception.getFieldErrors());
        }
    }

    private ModelAndView approvalError(String username, Long id, ApprovalForm form,
                                       Map<String, String> errors) {
        ExpenseDetailView detail = expenseService.findVisible(username, id);
        ModelAndView model = new ModelAndView("expenses/detail");
        model.setStatus(HttpStatus.BAD_REQUEST);
        model.addObject("currentUsername", username);
        model.addObject("currentIsApprover", expenseService.isApprover(username));
        model.addObject("detail", detail);
        model.addObject("approvalForm", form);
        model.addObject("approvalErrors", errors);
        model.addObject("hasApprovalError", true);
        return model;
    }

    private Map<String, String> bindingErrors(BindingResult bindingResult) {
        Map<String, String> errors = new LinkedHashMap<>();
        bindingResult.getFieldErrors().forEach(error -> errors.putIfAbsent(
                error.getField(), "version".equals(error.getField())
                        ? "versionの形式が不正です" : "入力値の形式が不正です"));
        return errors;
    }

    private ModelAndView seeOther(String path) {
        RedirectView redirectView = new RedirectView(path);
        redirectView.setStatusCode(HttpStatus.SEE_OTHER);
        redirectView.setExposeModelAttributes(false);
        return new ModelAndView(redirectView);
    }
}
