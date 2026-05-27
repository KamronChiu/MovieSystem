package com.eduaccess.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * Maps the local "uploads" directory to the "/uploads/**" URL so that
 * Manager-uploaded film posters can be served as static resources.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final String UPLOAD_ROOT = new File("uploads").getAbsolutePath();
    public static final String POSTERS_SUBDIR = "posters";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Ensure the directory exists at startup
        File posters = new File(UPLOAD_ROOT, POSTERS_SUBDIR);
        if (!posters.exists()) {
            posters.mkdirs();
        }

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + UPLOAD_ROOT + File.separator);
    }
}
