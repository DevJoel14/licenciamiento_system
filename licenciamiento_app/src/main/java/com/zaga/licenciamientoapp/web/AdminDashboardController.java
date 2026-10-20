package com.zaga.licenciamientoapp.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Sirve el panel de administracion (consume la API REST con la cabecera de admin). */
@Controller
public class AdminDashboardController {

    @GetMapping("/admin")
    public String panel() {
        return "admin";
    }
}
