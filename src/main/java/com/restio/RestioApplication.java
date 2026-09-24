package com.restio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "Restio", sharedModules = "shared")
@SpringBootApplication
public class RestioApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestioApplication.class, args);
    }
}
