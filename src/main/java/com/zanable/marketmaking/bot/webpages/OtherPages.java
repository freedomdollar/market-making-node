package com.zanable.marketmaking.bot.webpages;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OtherPages {

    @GetMapping(value="/settings", produces={"text/html"})
    public String displaySettingsPage(Model model) {

        return "settings2.html";
    }

    @GetMapping(value="/transactions", produces={"text/html"})
    public String displayTransactionsPage(Model model) {

        return "wallet-transactions.html";
    }

    @GetMapping(value="/transfer-funds", produces={"text/html"})
    public String transferFundsPage(Model model) {

        return "transfer-funds.html";
    }
}
