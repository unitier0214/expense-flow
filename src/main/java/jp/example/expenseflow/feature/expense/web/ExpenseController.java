package jp.example.expenseflow.feature.expense.web;

import java.util.LinkedHashMap;
import java.util.Map;
import jp.example.expenseflow.feature.expense.service.ExpenseInputException;
import jp.example.expenseflow.feature.expense.service.ExpenseService;
import jp.example.expenseflow.feature.expense.service.dto.ExpenseDetailView;
import jp.example.expenseflow.feature.expense.service.dto.ExpenseForm;
import jp.example.expenseflow.feature.expense.service.dto.ExpenseListPage;
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
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/expenses";
    }

    @GetMapping("/expenses")
    public ModelAndView list(Authentication authentication,
                             @RequestParam(required = false) String status,
                             @RequestParam(required = false) String category,
                             @RequestParam(required = false) String from,
                             @RequestParam(required = false) String to,
                             @RequestParam(required = false) String q,
                             @RequestParam(defaultValue = "0") int page) {
        ExpenseListPage listPage = expenseService.findOwn(authentication.getName(), status,
                category, from, to, q, page);
        ModelAndView model = new ModelAndView("expenses/index");
        model.addObject("currentUsername", authentication.getName());
        model.addObject("page", listPage);
        model.addObject("statuses", ExpenseService.statusLabels());
        model.addObject("categories", ExpenseService.categoryLabels());
        return model;
    }

    @GetMapping("/expenses/new")
    public ModelAndView newExpense(Authentication authentication) {
        ModelAndView model = formModel(new ExpenseForm(), false, null, authentication.getName());
        model.addObject("pageTitle", "経費申請を作成");
        model.addObject("submitLabel", "下書きを保存");
        return model;
    }

    @PostMapping("/expenses")
    public ModelAndView create(Authentication authentication,
                               @ModelAttribute("form") ExpenseForm form,
                               BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return formError(form, false, null, authentication.getName(), bindingErrors(bindingResult));
        }
        try {
            Long id = expenseService.create(authentication.getName(), form);
            return seeOther("/expenses/" + id);
        } catch (ExpenseInputException exception) {
            return formError(exception.getForm(), false, null, authentication.getName(),
                    exception.getFieldErrors());
        }
    }

    @GetMapping("/expenses/{id}")
    public ModelAndView detail(Authentication authentication, @PathVariable Long id) {
        ExpenseDetailView detail = expenseService.findVisible(authentication.getName(), id);
        ModelAndView model = new ModelAndView("expenses/detail");
        model.addObject("currentUsername", authentication.getName());
        model.addObject("detail", detail);
        return model;
    }

    @GetMapping("/expenses/{id}/edit")
    public ModelAndView edit(Authentication authentication, @PathVariable Long id) {
        ExpenseForm form = expenseService.prepareEdit(authentication.getName(), id);
        ModelAndView model = formModel(form, true, id, authentication.getName());
        model.addObject("pageTitle", "経費申請を編集");
        model.addObject("submitLabel", "変更を保存");
        return model;
    }

    @PostMapping("/expenses/{id}/edit")
    public ModelAndView update(Authentication authentication, @PathVariable Long id,
                               @ModelAttribute("form") ExpenseForm form,
                               BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return formError(form, true, id, authentication.getName(), bindingErrors(bindingResult));
        }
        try {
            expenseService.update(authentication.getName(), id, form);
            return seeOther("/expenses/" + id);
        } catch (ExpenseInputException exception) {
            return formError(exception.getForm(), true, id, authentication.getName(),
                    exception.getFieldErrors());
        }
    }

    @PostMapping("/expenses/{id}/delete")
    public ModelAndView delete(Authentication authentication, @PathVariable Long id,
                               @RequestParam(name = "version", required = false) Long version) {
        expenseService.delete(authentication.getName(), id, version);
        return seeOther("/expenses");
    }

    @PostMapping("/expenses/{id}/submit")
    public ModelAndView submit(Authentication authentication, @PathVariable Long id,
                               @RequestParam(name = "version", required = false) Long version) {
        expenseService.submit(authentication.getName(), id, version);
        return seeOther("/expenses/" + id);
    }

    private ModelAndView formModel(ExpenseForm form, boolean edit, Long editTargetId,
                                   String username) {
        ModelAndView model = new ModelAndView("expenses/form");
        model.addObject("currentUsername", username);
        model.addObject("form", form);
        model.addObject("isEdit", edit);
        model.addObject("editTargetId", editTargetId);
        model.addObject("categories", ExpenseService.categoryLabels());
        model.addObject("formAction", edit && editTargetId != null
                ? "/expenses/" + editTargetId + "/edit" : "/expenses");
        model.addObject("fieldErrors", Map.of());
        return model;
    }

    private ModelAndView formError(ExpenseForm form, boolean edit, Long editTargetId,
                                   String username,
                                   Map<String, String> fieldErrors) {
        ModelAndView model = formModel(form, edit, editTargetId, username);
        model.setStatus(HttpStatus.BAD_REQUEST);
        model.addObject("pageTitle", edit ? "経費申請を編集" : "経費申請を作成");
        model.addObject("submitLabel", edit ? "変更を保存" : "下書きを保存");
        model.addObject("fieldErrors", fieldErrors);
        model.addObject("hasFormError", true);
        return model;
    }

    private Map<String, String> bindingErrors(BindingResult bindingResult) {
        Map<String, String> errors = new LinkedHashMap<>();
        bindingResult.getFieldErrors().forEach(error -> errors.putIfAbsent(
                error.getField(), messageForBindingError(error.getField())));
        return errors;
    }

    private String messageForBindingError(String field) {
        if ("version".equals(field)) {
            return "versionの形式が不正です";
        }
        return "入力値の形式が不正です";
    }

    private ModelAndView seeOther(String path) {
        RedirectView redirectView = new RedirectView(path);
        redirectView.setStatusCode(HttpStatus.SEE_OTHER);
        redirectView.setExposeModelAttributes(false);
        return new ModelAndView(redirectView);
    }
}
