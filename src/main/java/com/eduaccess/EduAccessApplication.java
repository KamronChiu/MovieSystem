package com.eduaccess;

import com.vaadin.flow.component.dependency.CssImport;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@CssImport("./styles/hcbs-theme.css")
public class EduAccessApplication {
    public static void main(String[] args) {
        SpringApplication.run(EduAccessApplication.class, args);
    }
}