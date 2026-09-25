package service;

/**
 * File Location: src/service/SecurityManager.java
 * Subclasses CloudSecurityManager to maintain backward-compatibility with project requirements
 * while avoiding ambiguity with java.lang.SecurityManager.
 */
public class SecurityManager extends CloudSecurityManager {
    public SecurityManager() {
        super();
    }
}
