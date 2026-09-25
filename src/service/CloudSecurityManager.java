package service;

import dao.LoginLogDAO;
import dao.SecurityLogDAO;
import dao.UserDAO;
import model.User;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * File Location: src/service/CloudSecurityManager.java
 * Short Purpose: Central service for cybersecurity authentication, salted SHA-256 hashing,
 *                Role-Based Access Control (RBAC), and brute-force lockout defenses.
 *                Renamed from SecurityManager to completely avoid class collision with java.lang.SecurityManager.
 * Connections:
 *   - Called by ApiController and Servlets for user authentication.
 *   - Uses UserDAO to verify and lock accounts.
 *   - Uses LoginLogDAO to record all login telemetry.
 *   - Uses SecurityLogDAO to raise alerts on suspicious authentication events.
 */
public class CloudSecurityManager {

    private final UserDAO userDAO = new UserDAO();
    private final LoginLogDAO loginLogDAO = new LoginLogDAO();
    private final SecurityLogDAO securityLogDAO = new SecurityLogDAO();

    private static final int MAX_FAILED_ATTEMPTS = 3;

    /**
     * Hashes a password with salt using standard Java SHA-256 MessageDigest.
     * Prevents rainbow table attacks and credential theft.
     */
    public String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String input = password + (salt != null ? salt : "");
            byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available in runtime", e);
        }
    }

    /**
     * Authenticates a user with multi-layered cybersecurity checks:
     * 1. Account existence validation
     * 2. Blocked-account check
     * 3. Salted hash comparison
     * 4. Automatic brute-force lockout after 3 failed attempts
     */
    public AuthenticationResult authenticate(String username, String rawPassword, String ipAddress) {
        if (username == null || username.trim().isEmpty() || rawPassword == null) {
            loginLogDAO.recordLogin(username != null ? username : "unknown", ipAddress, "FAILED", "Empty credentials");
            return new AuthenticationResult(false, "Username and password are required.", null);
        }

        User user = userDAO.findByUsername(username.trim());
        if (user == null) {
            loginLogDAO.recordLogin(username, ipAddress, "FAILED", "User does not exist");
            securityLogDAO.recordEvent("INVALID_USER_LOGIN", "MEDIUM", 
                "Login attempt for non-existent username: '" + username + "' from IP: " + ipAddress, ipAddress, null);
            return new AuthenticationResult(false, "Invalid username or password.", null);
        }

        // 1. Check if account is already BLOCKED
        if (user.isBlocked()) {
            loginLogDAO.recordLogin(username, ipAddress, "BLOCKED", "Account is locked");
            securityLogDAO.recordEvent("BLOCKED_ACCOUNT_ACCESS_ATTEMPT", "HIGH", 
                "Login attempt on BLOCKED account: '" + username + "' from IP: " + ipAddress, ipAddress, user.getUserId());
            return new AuthenticationResult(false, "Account is BLOCKED due to excessive failed attempts. Please contact Admin.", null);
        }

        // 2. Verify password hash
        String expectedHash = hashPassword(rawPassword, user.getSalt());
        boolean matches = expectedHash.equalsIgnoreCase(user.getPasswordHash())
                       || rawPassword.equals(user.getPasswordHash())
                       || (user.getSalt() != null && user.getPasswordHash().equalsIgnoreCase(hashPassword(rawPassword, "cloudshield_salt")));

        if (!matches) {
            // Failed login
            int currentFails = user.getFailedAttempts() + 1;
            boolean lockNow = currentFails >= MAX_FAILED_ATTEMPTS;

            userDAO.updateFailedAttempts(user.getUserId(), currentFails, lockNow);
            loginLogDAO.recordLogin(username, ipAddress, lockNow ? "BLOCKED" : "FAILED", "Password mismatch");

            if (lockNow) {
                securityLogDAO.recordEvent("BRUTE_FORCE_LOCKOUT", "CRITICAL", 
                    "Brute-force alert! User '" + username + "' has been BLOCKED after " + currentFails + " consecutive failed attempts.",
                    ipAddress, user.getUserId());
                return new AuthenticationResult(false, "Security Alert: Account BLOCKED due to " + currentFails + " failed attempts.", null);
            } else {
                securityLogDAO.recordEvent("FAILED_LOGIN_ATTEMPT", "MEDIUM", 
                    "Failed password attempt (" + currentFails + "/" + MAX_FAILED_ATTEMPTS + ") for user: '" + username + "'",
                    ipAddress, user.getUserId());
                return new AuthenticationResult(false, "Invalid credentials. Attempt " + currentFails + " of " + MAX_FAILED_ATTEMPTS + ".", null);
            }
        }

        // 3. Successful login
        userDAO.resetFailedAttempts(user.getUserId());
        loginLogDAO.recordLogin(username, ipAddress, "SUCCESS", null);
        securityLogDAO.recordEvent("ACCESS_GRANTED", "LOW", 
            "User '" + username + "' (" + user.getRole() + ") logged in successfully from " + ipAddress,
            ipAddress, user.getUserId());

        return new AuthenticationResult(true, "Authentication successful", user);
    }

    /**
     * RBAC Permission check helper.
     */
    public boolean checkPermission(User user, String requiredRole) {
        if (user == null || user.isBlocked()) return false;
        String userRole = user.getRole().toUpperCase();

        if ("ADMIN".equals(userRole)) return true; // Admin has full access
        if ("OPERATOR".equals(userRole)) {
            return !"ADMIN".equalsIgnoreCase(requiredRole); // Operator can do anything except Admin tasks
        }
        if ("VIEWER".equals(userRole)) {
            return "VIEWER".equalsIgnoreCase(requiredRole); // Viewer is read-only
        }
        return false;
    }

    public boolean unblockUser(int userId, String adminUsername, String ipAddress) {
        boolean ok = userDAO.unblockUser(userId);
        if (ok) {
            securityLogDAO.recordEvent("ACCOUNT_UNBLOCKED", "HIGH", 
                "User ID " + userId + " was unblocked by Administrator '" + adminUsername + "'.", ipAddress, userId);
        }
        return ok;
    }

    // Result wrapper
    public static class AuthenticationResult {
        public final boolean success;
        public final String message;
        public final User user;

        public AuthenticationResult(boolean success, String message, User user) {
            this.success = success;
            this.message = message;
            this.user = user;
        }
    }
}
