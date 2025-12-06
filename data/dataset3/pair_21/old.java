package org.example.service;

import java.util.logging.Logger;

public class UserService {
    private Logger logger = Logger.getLogger("UserService");
    
    public boolean validateEmail(String email) {
        if (email == null || email.isEmpty()) {
            logger.warning("Email validation failed: empty email");
            return false;
        }
        
        if (!email.contains("@")) {
            logger.warning("Email validation failed: missing @ symbol");
            return false;
        }
        
        if (email.length() > 255) {
            logger.warning("Email validation failed: exceeds maximum length");
            return false;
        }
        
        logger.info("Email validation successful");
        return true;
    }
    
    public String getUserRole(String username) {
        if (username.equals("admin")) {
            return "ROLE_ADMIN";
        } else if (username.equals("moderator")) {
            return "ROLE_MODERATOR";
        } else {
            return "ROLE_USER";
        }
    }
    
    public void processUser(String status) {
        if (status.equals("active")) {
            logger.info("Processing active user");
        } else if (status.equals("inactive")) {
            logger.info("Processing inactive user");
        } else if (status.equals("suspended")) {
            logger.info("Processing suspended user");
        }
    }
}