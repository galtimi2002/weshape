package com.weshape.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// UserDetailsServiceAutoConfiguration is excluded because this API has no form
// login: it would otherwise create an in-memory user and print a random
// "generated security password" into the logs on every boot.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class WeShapeApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeShapeApplication.class, args);
    }
}
