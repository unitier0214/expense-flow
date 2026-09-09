package jp.example.expenseflow.feature.expense.web;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ExpensePlaceholderController {

    @GetMapping("/")
    public String root() {
        return "redirect:/expenses";
    }

    @GetMapping("/expenses")
    public String expenses(Authentication authentication, Model model) {
        model.addAttribute("currentUsername", authentication.getName());
        return "expenses/index";
    }
}
