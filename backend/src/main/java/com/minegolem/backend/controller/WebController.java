package com.minegolem.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class WebController {

    @ModelAttribute
    public void addCurrentPath(Model model, HttpServletRequest request) {
        model.addAttribute("currentPath", request.getRequestURI());
    }

    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("title", "Login");
        return "login";
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("title", "Dashboard");
        return "dashboard";
    }

    @GetMapping("/clients/new")
    public String newClient(Model model) {
        model.addAttribute("title", "Nuovo Cliente");
        return "client-form";
    }

    @GetMapping("/clients")
    public String clients(Model model) {
        model.addAttribute("title", "Clienti");
        return "clients";
    }

    @GetMapping("/clients/{id}")
    public String clientDetail(@PathVariable("id") String id, Model model) {
        model.addAttribute("title", "Dettaglio Cliente");
        model.addAttribute("clientId", id);
        return "client-detail";
    }

    @GetMapping("/accesses")
    public String accesses(Model model) {
        model.addAttribute("title", "Accessi");
        return "accesses";
    }

    @GetMapping("/payments")
    public String payments(Model model) {
        model.addAttribute("title", "Incassi");
        return "payments";
    }

    @GetMapping("/subscriptions")
    public String subscriptions(Model model) {
        model.addAttribute("title", "Abbonamenti");
        return "subscriptions";
    }

    @GetMapping("/emails")
    public String emails(Model model) {
        model.addAttribute("title", "Invio Email");
        return "emails";
    }

    @GetMapping("/reports")
    public String reports(Model model) {
        model.addAttribute("title", "Report");
        return "reports";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("title", "Impostazioni");
        return "settings";
    }
}
