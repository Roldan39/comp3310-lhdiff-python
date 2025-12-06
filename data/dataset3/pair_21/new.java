package org.example.service;

import java.util.logging.Logger;

public class UserService {
    private static final String LOGGER_NAME = "UserService";
    private static final int MAX_EMAIL_LENGTH = 255;
    private static final String EMAIL_SYMBOL = "@";
    
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String ROLE_MODERATOR = "ROLE_MODERATOR";
    private static final String ROLE_USER = "ROLE_USER";
    
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_INACTIVE = "inactive";
    private static final String STATUS_SUSPENDED = "suspended";
    
    private Logger logger = Logger.getLogger(LOGGER_NAME);
    
    public boolean validateEmail(String email) {
        if (email == null || email.isEmpty()) {
            logger.warning("Email validation failed: empty email");
            return false;
        }
        
        if (!email.contains(EMAIL_SYMBOL)) {
            logger.warning("Email validation failed: missing @ symbol");
            return false;
        }
        
        if (email.length() > MAX_EMAIL_LENGTH) {
            logger.warning("Email validation failed: exceeds maximum length");
            return false;
        }
        
        logger.info("Email validation successful");
        return true;
    }
    
    public String getUserRole(String username) {
        if (username.equals("admin")) {
            return ROLE_ADMIN;
        } else if (username.equals("moderator")) {
            return ROLE_MODERATOR;
        } else {
            return ROLE_USER;
        }
    }
    
    public void processUser(String status) {
        if (status.equals(STATUS_ACTIVE)) {
            logger.info("Processing active user");
        } else if (status.equals(STATUS_INACTIVE)) {
            logger.info("Processing inactive user");
        } else if (status.equals(STATUS_SUSPENDED)) {
            logger.info("Processing suspended user");
        }
    }
}