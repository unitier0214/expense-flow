package jp.example.expenseflow.feature.expense.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ExpensePlaceholderController {

    @GetMapping("/")
    public String root() {
        return "redirect:/expenses";
    }

    @GetMapping("/expenses")
    public String expenses() {
        return "expenses/index";
    }
}
