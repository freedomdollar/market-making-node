package com.zanable.marketmaking.bot.webpages;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexPage {

    @GetMapping(value="/", produces={"text/html"})
    public String displaySetupPage(Model model) {

        return "dashboard.html";
    }
}
