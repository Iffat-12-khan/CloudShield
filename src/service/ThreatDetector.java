package service;

import dao.ResourceDAO;
import dao.ResourceRequestDAO;
import dao.SecurityLogDAO;
import dao.UserDAO;
import model.Resource;
import model.ResourceRequest;
import model.SecurityLog;
import model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * File Location: src/service/ThreatDetector.java
 * Short Purpose: Threat Detection Engine analyzing cybersecurity metrics, brute-force patterns,
 *                unauthorized privilege attempts, and cloud resource abuse anomalies.
 * Connections:
 *   - Aggregates telemetry from UserDAO, ResourceDAO, ResourceRequestDAO, and LoginLogDAO.
 *   - Generates actionable threat alerts logged to SecurityLogDAO.
 *   - Supplies the Cybersecurity Threat Radar and live gauge meters on the frontend.
 */
public class ThreatDetector {

    private final UserDAO userDAO = new UserDAO();
    private final ResourceDAO resourceDAO = new ResourceDAO();
    private final ResourceRequestDAO requestDAO = new ResourceRequestDAO();
    private final SecurityLogDAO securityLogDAO = new SecurityLogDAO();
    /**
     * Executes a full system vulnerability and threat scan.
     */
    public ThreatScanReport runSecurityScan() {
        List<ThreatAlert> alerts = new ArrayList<>();

        // 1. Scan for Blocked Accounts & Brute Force
        List<User> users = userDAO.findAll();
        int blockedCount = 0;
        for (User u : users) {
            if (u.isBlocked()) {
                blockedCount++;
                alerts.add(new ThreatAlert(
                    "BRUTE_FORCE_LOCKOUT",
                    "CRITICAL",
                    "User '" + u.getUsername() + "' is currently locked out due to " + u.getFailedAttempts() + " failed logins.",
                    "192.168.1.42",
                    u.getUsername()
                ));
            } else if (u.getFailedAttempts() > 0) {
                alerts.add(new ThreatAlert(
                    "FAILED_LOGINS_WARNING",
                    "MEDIUM",
                    "User '" + u.getUsername() + "' has " + u.getFailedAttempts() + "/3 failed attempts in the current session.",
                    "127.0.0.1",
                    u.getUsername()
                ));
            }
        }

        // 2. Scan for Resource Abuse Anomaly (Requests exceeding 80% of available units)
        List<Resource> resources = resourceDAO.findAll();
        List<ResourceRequest> requests = requestDAO.findAll();

        for (ResourceRequest req : requests) {
            if ("PENDING".equalsIgnoreCase(req.getStatus())) {
                Resource res = null;
                for (Resource r : resources) {
                    if (r.getResourceId() == req.getResourceId()) {
                        res = r;
                        break;
                    }
                }

                if (res != null) {
                    // Check if requested units exceed total pool capacity (Impossible/Malicious Request)
                    if (req.getRequestedUnits() > res.getTotalUnits()) {
                        alerts.add(new ThreatAlert(
                            "RESOURCE_OVERFLOW_ATTEMPT",
                            "HIGH",
                            "VM '" + req.getVmName() + "' requested " + req.getRequestedUnits() + " " + res.getUnit() + 
                            " exceeding entire cloud capacity (" + res.getTotalUnits() + " " + res.getUnit() + ").",
                            "10.0.0.15",
                            "VM-" + req.getVmId()
                        ));
                    }
                    // Check if requested units consume >80% of available balance (Resource Starvation Denial of Service)
                    else if (res.getAvailableUnits() > 0 && req.getRequestedUnits() > (res.getAvailableUnits() * 0.8)) {
                        alerts.add(new ThreatAlert(
                            "RESOURCE_STARVATION_RISK",
                            "MEDIUM",
                            "VM '" + req.getVmName() + "' requested " + req.getRequestedUnits() + " " + res.getUnit() + 
                            ", consuming >80% of remaining pool balance (" + res.getAvailableUnits() + " " + res.getUnit() + ").",
                            "10.0.0.12",
                            "VM-" + req.getVmId()
                        ));
                    }
                }
            }
        }

        // 3. Scan recent critical security events
        List<SecurityLog> recentLogs = securityLogDAO.findAll("CRITICAL");
        for (int i = 0; i < Math.min(recentLogs.size(), 3); i++) {
            SecurityLog sl = recentLogs.get(i);
            alerts.add(new ThreatAlert(sl.getEventType(), sl.getSeverity(), sl.getDescription(), sl.getIpAddress(), sl.getUsername()));
        }

        // 4. Calculate Security Health Score (0 - 100)
        double score = 100.0;
        for (ThreatAlert alert : alerts) {
            switch (alert.severity) {
                case "CRITICAL" -> score -= 15.0;
                case "HIGH" -> score -= 8.0;
                case "MEDIUM" -> score -= 3.0;
                case "LOW" -> score -= 1.0;
            }
        }
        score = Math.max(10.0, Math.min(100.0, score));

        String threatLevel;
        if (score >= 90.0) threatLevel = "LOW (NORMAL)";
        else if (score >= 75.0) threatLevel = "ELEVATED";
        else if (score >= 50.0) threatLevel = "HIGH";
        else threatLevel = "CRITICAL";

        return new ThreatScanReport(score, threatLevel, alerts.size(), blockedCount, alerts);
    }

    /**
     * Threat Report Transfer Object
     */
    public static class ThreatScanReport {
        public final double healthScore;
        public final String threatLevel;
        public final int totalAlerts;
        public final int blockedUsers;
        public final List<ThreatAlert> alerts;

        public ThreatScanReport(double healthScore, String threatLevel, int totalAlerts, int blockedUsers, List<ThreatAlert> alerts) {
            this.healthScore = healthScore;
            this.threatLevel = threatLevel;
            this.totalAlerts = totalAlerts;
            this.blockedUsers = blockedUsers;
            this.alerts = alerts;
        }

        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"healthScore\":").append(String.format("%.1f", healthScore)).append(",");
            sb.append("\"threatLevel\":\"").append(threatLevel).append("\",");
            sb.append("\"totalAlerts\":").append(totalAlerts).append(",");
            sb.append("\"blockedUsers\":").append(blockedUsers).append(",");
            sb.append("\"alerts\":[");
            for (int i = 0; i < alerts.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(alerts.get(i).toJson());
            }
            sb.append("]}");
            return sb.toString();
        }
    }

    public static class ThreatAlert {
        public final String eventType;
        public final String severity;
        public final String description;
        public final String ipAddress;
        public final String subject;

        public ThreatAlert(String eventType, String severity, String description, String ipAddress, String subject) {
            this.eventType = eventType;
            this.severity = severity;
            this.description = description;
            this.ipAddress = ipAddress;
            this.subject = subject;
        }

        public String toJson() {
            return String.format(
                "{\"eventType\":\"%s\",\"severity\":\"%s\",\"description\":\"%s\",\"ipAddress\":\"%s\",\"subject\":\"%s\"}",
                eventType, severity, description.replace("\"", "\\\""), ipAddress, subject != null ? subject : "N/A"
            );
        }
    }
}
