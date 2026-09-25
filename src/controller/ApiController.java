package controller;

import dao.*;
import model.*;
import service.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * File Location: src/controller/ApiController.java
 * Short Purpose: Central RESTful API Controller following the MVC architecture.
 *                Dispatches JSON requests to appropriate Services and DAOs.
 * Connections:
 *   - Receives HTTP requests from CloudShieldServlet (Tomcat) or Main (Embedded Server).
 *   - Calls SecurityManager, DeadlockDetector, ThreatDetector, and ResourceManager.
 *   - Returns standardized JSON payloads to the frontend JavaScript client.
 */
public class ApiController {

    private final CloudSecurityManager securityManager = new CloudSecurityManager();
    private final DeadlockDetector deadlockDetector = new DeadlockDetector();
    private final ThreatDetector threatDetector = new ThreatDetector();
    private final ResourceManager resourceManager = new ResourceManager();
    private final UserDAO userDAO = new UserDAO();
    private final SecurityLogDAO securityLogDAO = new SecurityLogDAO();
    private final LoginLogDAO loginLogDAO = new LoginLogDAO();
    private final DeadlockLogDAO deadlockLogDAO = new DeadlockLogDAO();

    // Active session store (token -> User)
    private static final Map<String, User> sessions = new HashMap<>();

    public static class ApiResponse {
        public final int statusCode;
        public final String contentType;
        public final String body;

        public ApiResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.contentType = "application/json; charset=UTF-8";
            this.body = body;
        }

        public static ApiResponse ok(String json) {
            return new ApiResponse(200, json);
        }

        public static ApiResponse badRequest(String error) {
            return new ApiResponse(400, "{\"success\":false,\"error\":\"" + escapeJson(error) + "\"}");
        }

        public static ApiResponse unauthorized(String error) {
            return new ApiResponse(401, "{\"success\":false,\"error\":\"" + escapeJson(error) + "\"}");
        }

        public static ApiResponse serverError(String error) {
            return new ApiResponse(500, "{\"success\":false,\"error\":\"" + escapeJson(error) + "\"}");
        }
    }

    /**
     * Routes and handles all /api/* requests.
     */
    public ApiResponse handleRequest(String method, String path, Map<String, String> queryParams, 
                                     Map<String, String> headers, InputStream requestBodyStream, String clientIp) {
        try {
            String pathLower = path.toLowerCase();
            String token = headers.getOrDefault("authorization", headers.getOrDefault("Authorization", ""));
            if (token.startsWith("Bearer ")) token = token.substring(7).trim();
            User currentUser = sessions.get(token);

            // Read request body string
            String body = readStream(requestBodyStream);
            Map<String, String> bodyParams = parseJsonOrForm(body);

            // ----------------------------------------------------------------
            // 1. AUTHENTICATION ENDPOINTS
            // ----------------------------------------------------------------
            if (pathLower.equals("/api/auth/login")) {
                String username = bodyParams.getOrDefault("username", "");
                String password = bodyParams.getOrDefault("password", "");
                var res = securityManager.authenticate(username, password, clientIp);

                if (res.success && res.user != null) {
                    String newToken = UUID.randomUUID().toString();
                    sessions.put(newToken, res.user);
                    return ApiResponse.ok(String.format(
                        "{\"success\":true,\"message\":\"%s\",\"token\":\"%s\",\"user\":%s}",
                        escapeJson(res.message), newToken, res.user.toJson()
                    ));
                } else {
                    return ApiResponse.unauthorized(res.message);
                }
            }

            if (pathLower.equals("/api/auth/logout")) {
                if (token != null) sessions.remove(token);
                return ApiResponse.ok("{\"success\":true,\"message\":\"Logged out successfully\"}");
            }

            if (pathLower.equals("/api/auth/me")) {
                if (currentUser == null) return ApiResponse.unauthorized("Not logged in");
                return ApiResponse.ok(String.format("{\"success\":true,\"user\":%s}", currentUser.toJson()));
            }

            if (pathLower.equals("/api/auth/users")) {
                List<User> list = userDAO.findAll();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(list.get(i).toJson());
                }
                sb.append("]");
                return ApiResponse.ok(sb.toString());
            }

            // ----------------------------------------------------------------
            // 2. DASHBOARD & KPIS
            // ----------------------------------------------------------------
            if (pathLower.equals("/api/dashboard/stats")) {
                List<VirtualMachine> vms = resourceManager.getAllVMs();
                List<Resource> resources = resourceManager.getAllResources();
                List<ResourceRequest> requests = resourceManager.getAllRequests();
                var scan = threatDetector.runSecurityScan();
                var deadlockResult = deadlockDetector.detectDeadlocks();

                int totalVms = vms.size();
                long runningVms = vms.stream().filter(v -> "RUNNING".equalsIgnoreCase(v.getStatus())).count();
                long waitingVms = vms.stream().filter(v -> "WAITING".equalsIgnoreCase(v.getStatus())).count();

                int totalCores = 0, availCores = 0;
                int totalRam = 0, availRam = 0;
                for (Resource r : resources) {
                    if ("CPU".equalsIgnoreCase(r.getResourceName())) {
                        totalCores = r.getTotalUnits();
                        availCores = r.getAvailableUnits();
                    } else if ("RAM".equalsIgnoreCase(r.getResourceName())) {
                        totalRam = r.getTotalUnits();
                        availRam = r.getAvailableUnits();
                    }
                }

                long pendingReqs = requests.stream().filter(r -> "PENDING".equalsIgnoreCase(r.getStatus())).count();

                return ApiResponse.ok(String.format(
                    "{\"totalVms\":%d,\"runningVms\":%d,\"waitingVms\":%d,\"totalCores\":%d,\"availableCores\":%d,\"totalRam\":%d,\"availableRam\":%d,\"pendingRequests\":%d,\"threatScore\":%.1f,\"threatLevel\":\"%s\",\"deadlockRisk\":%b,\"cycleCount\":%d}",
                    totalVms, runningVms, waitingVms, totalCores, availCores, totalRam, availRam, pendingReqs,
                    scan.healthScore, scan.threatLevel, deadlockResult.deadlockDetected, deadlockResult.cycles.size()
                ));
            }

            // ----------------------------------------------------------------
            // 3. VIRTUAL MACHINES (CRUD)
            // ----------------------------------------------------------------
            if (pathLower.equals("/api/vms")) {
                if ("GET".equalsIgnoreCase(method)) {
                    List<VirtualMachine> vms = resourceManager.getAllVMs();
                    StringBuilder sb = new StringBuilder("[");
                    for (int i = 0; i < vms.size(); i++) {
                        if (i > 0) sb.append(",");
                        sb.append(vms.get(i).toJson());
                    }
                    sb.append("]");
                    return ApiResponse.ok(sb.toString());
                }

                if ("POST".equalsIgnoreCase(method)) {
                    // RBAC: Requires Operator or Admin
                    if (currentUser != null && "VIEWER".equalsIgnoreCase(currentUser.getRole())) {
                        return ApiResponse.unauthorized("Viewer role does not have permission to deploy Virtual Machines.");
                    }

                    String name = bodyParams.getOrDefault("vmName", "New-VM-" + System.currentTimeMillis() % 1000);
                    String os = bodyParams.getOrDefault("operatingSystem", "Ubuntu 22.04 LTS");
                    int cpu = parseInt(bodyParams.get("cpuRequired"), 2);
                    int ram = parseInt(bodyParams.get("ramRequired"), 4);
                    int storage = parseInt(bodyParams.get("storageRequired"), 50);
                    int ownerId = currentUser != null ? currentUser.getUserId() : 1;

                    VirtualMachine vm = new VirtualMachine(0, name, os, cpu, ram, storage, "RUNNING", ownerId, null);
                    boolean ok = resourceManager.createVM(vm);
                    return ok 
                        ? ApiResponse.ok("{\"success\":true,\"message\":\"Virtual Machine deployed successfully\",\"vm\":" + vm.toJson() + "}")
                        : ApiResponse.badRequest("Failed to deploy Virtual Machine.");
                }

                if ("PUT".equalsIgnoreCase(method)) {
                    int vmId = parseInt(bodyParams.get("vmId"), -1);
                    String status = bodyParams.getOrDefault("status", "RUNNING");
                    boolean ok = resourceManager.changeVMStatus(vmId, status);
                    return ApiResponse.ok("{\"success\":" + ok + "}");
                }

                if ("DELETE".equalsIgnoreCase(method)) {
                    int vmId = parseInt(queryParams.getOrDefault("vmId", bodyParams.get("vmId")), -1);
                    boolean ok = resourceManager.deleteVM(vmId);
                    return ApiResponse.ok("{\"success\":" + ok + "}");
                }
            }

            // ----------------------------------------------------------------
            // 4. CLOUD RESOURCES
            // ----------------------------------------------------------------
            if (pathLower.equals("/api/resources")) {
                List<Resource> list = resourceManager.getAllResources();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(list.get(i).toJson());
                }
                sb.append("]");
                return ApiResponse.ok(sb.toString());
            }

            // ----------------------------------------------------------------
            // 5. RESOURCE REQUESTS
            // ----------------------------------------------------------------
            if (pathLower.equals("/api/requests")) {
                if ("GET".equalsIgnoreCase(method)) {
                    List<ResourceRequest> reqs = resourceManager.getAllRequests();
                    StringBuilder sb = new StringBuilder("[");
                    for (int i = 0; i < reqs.size(); i++) {
                        if (i > 0) sb.append(",");
                        sb.append(reqs.get(i).toJson());
                    }
                    sb.append("]");
                    return ApiResponse.ok(sb.toString());
                }

                if ("POST".equalsIgnoreCase(method)) {
                    int vmId = parseInt(bodyParams.get("vmId"), 1);
                    int resId = parseInt(bodyParams.get("resourceId"), 1);
                    int units = parseInt(bodyParams.get("requestedUnits"), 2);
                    boolean ok = resourceManager.submitResourceRequest(vmId, resId, units);
                    return ok 
                        ? ApiResponse.ok("{\"success\":true,\"message\":\"Resource request queued successfully.\"}")
                        : ApiResponse.badRequest("Request rejected due to invalid parameters or capacity limits.");
                }
            }

            if (pathLower.equals("/api/requests/approve")) {
                if (currentUser != null && "VIEWER".equalsIgnoreCase(currentUser.getRole())) {
                    return ApiResponse.unauthorized("Viewer role cannot approve resource allocations.");
                }
                int reqId = parseInt(bodyParams.get("requestId"), -1);
                boolean ok = resourceManager.approveAndAllocate(reqId);
                return ok
                    ? ApiResponse.ok("{\"success\":true,\"message\":\"Request approved and resource allocated via ACID transaction!\"}")
                    : ApiResponse.badRequest("Insufficient available resources to allocate request.");
            }

            if (pathLower.equals("/api/requests/reject")) {
                int reqId = parseInt(bodyParams.get("requestId"), -1);
                boolean ok = resourceManager.rejectRequest(reqId);
                return ApiResponse.ok("{\"success\":" + ok + "}");
            }

            // ----------------------------------------------------------------
            // 6. RESOURCE ALLOCATIONS
            // ----------------------------------------------------------------
            if (pathLower.equals("/api/allocations")) {
                List<Allocation> allocs = resourceManager.getActiveAllocations();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < allocs.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(allocs.get(i).toJson());
                }
                sb.append("]");
                return ApiResponse.ok(sb.toString());
            }

            if (pathLower.equals("/api/allocations/release")) {
                int allocId = parseInt(bodyParams.get("allocationId"), -1);
                boolean ok = resourceManager.releaseAllocation(allocId);
                return ApiResponse.ok("{\"success\":" + ok + ",\"message\":\"Resource successfully returned to available pool.\"}");
            }

            // ----------------------------------------------------------------
            // 7. DEADLOCK DETECTION & WAIT-FOR GRAPH (OS MODULE)
            // ----------------------------------------------------------------
            if (pathLower.equals("/api/deadlock/graph")) {
                var res = deadlockDetector.detectDeadlocks();
                return ApiResponse.ok(res.toJson());
            }

            if (pathLower.equals("/api/deadlock/detect")) {
                var res = deadlockDetector.detectDeadlocks();
                return ApiResponse.ok(res.toJson());
            }

            if (pathLower.equals("/api/deadlock/resolve")) {
                if (currentUser != null && "VIEWER".equalsIgnoreCase(currentUser.getRole())) {
                    return ApiResponse.unauthorized("Viewer role cannot execute deadlock resolution.");
                }
                var res = deadlockDetector.resolveDeadlock();
                return ApiResponse.ok(res.toJson());
            }

            if (pathLower.equals("/api/deadlock/simulate")) {
                // Creates a test deadlock cycle: VM 3 -> VM 2 -> VM 1 -> VM 3
                var waitDao = new WaitDependencyDAO();
                waitDao.addDependency(3, 2, 2);
                waitDao.addDependency(2, 1, 1);
                waitDao.addDependency(1, 3, 3);
                var res = deadlockDetector.detectDeadlocks();
                return ApiResponse.ok(res.toJson());
            }

            // ----------------------------------------------------------------
            // 8. CYBERSECURITY THREAT DETECTION
            // ----------------------------------------------------------------
            if (pathLower.equals("/api/threats/scan")) {
                var scan = threatDetector.runSecurityScan();
                return ApiResponse.ok(scan.toJson());
            }

            if (pathLower.equals("/api/threats/unblock")) {
                if (currentUser != null && !"ADMIN".equalsIgnoreCase(currentUser.getRole())) {
                    return ApiResponse.unauthorized("Only System Administrators can unblock user accounts.");
                }
                int userId = parseInt(bodyParams.get("userId"), -1);
                String adminName = currentUser != null ? currentUser.getUsername() : "admin";
                boolean ok = securityManager.unblockUser(userId, adminName, clientIp);
                return ApiResponse.ok("{\"success\":" + ok + ",\"message\":\"User account successfully unlocked.\"}");
            }

            if (pathLower.equals("/api/threats/simulate")) {
                String attackType = bodyParams.getOrDefault("type", "BRUTE_FORCE");
                if ("BRUTE_FORCE".equalsIgnoreCase(attackType)) {
                    // Simulate 3 rapid failed logins for john
                    securityManager.authenticate("john", "wrongpass1", "203.0.113.42");
                    securityManager.authenticate("john", "wrongpass2", "203.0.113.42");
                    var res = securityManager.authenticate("john", "wrongpass3", "203.0.113.42");
                    return ApiResponse.ok("{\"success\":true,\"message\":\"Brute-force simulation triggered: " + escapeJson(res.message) + "\"}");
                } else if ("RESOURCE_ABUSE".equalsIgnoreCase(attackType)) {
                    // Simulate huge request exceeding cloud storage
                    resourceManager.submitResourceRequest(3, 1, 9999);
                    return ApiResponse.ok("{\"success\":true,\"message\":\"Resource abuse anomaly simulated. Excessive core request logged to security audit.\"}");
                }
            }

            // ----------------------------------------------------------------
            // 9. AUDIT & SYSTEM LOGS
            // ----------------------------------------------------------------
            if (pathLower.equals("/api/logs/security")) {
                String severity = queryParams.getOrDefault("severity", "ALL");
                List<SecurityLog> logs = securityLogDAO.findAll(severity);
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < logs.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(logs.get(i).toJson());
                }
                sb.append("]");
                return ApiResponse.ok(sb.toString());
            }

            if (pathLower.equals("/api/logs/login")) {
                List<LoginLog> logs = loginLogDAO.findAll();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < logs.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(logs.get(i).toJson());
                }
                sb.append("]");
                return ApiResponse.ok(sb.toString());
            }

            if (pathLower.equals("/api/logs/deadlock")) {
                List<DeadlockLog> logs = deadlockLogDAO.findAll();
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < logs.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(logs.get(i).toJson());
                }
                sb.append("]");
                return ApiResponse.ok(sb.toString());
            }

            // ----------------------------------------------------------------
            // 10. SYSTEM REPORTS (SQL VIEWS)
            // ----------------------------------------------------------------
            if (pathLower.equals("/api/reports/summary")) {
                var scan = threatDetector.runSecurityScan();
                var dl = deadlockDetector.detectDeadlocks();
                List<Resource> resList = resourceManager.getAllResources();
                List<VirtualMachine> vmList = resourceManager.getAllVMs();

                StringBuilder sb = new StringBuilder();
                sb.append("{");
                sb.append("\"healthScore\":").append(String.format("%.1f", scan.healthScore)).append(",");
                sb.append("\"threatLevel\":\"").append(scan.threatLevel).append("\",");
                sb.append("\"activeDeadlocks\":").append(dl.deadlockDetected).append(",");
                sb.append("\"totalVms\":").append(vmList.size()).append(",");
                sb.append("\"totalResources\":").append(resList.size());
                sb.append("}");
                return ApiResponse.ok(sb.toString());
            }

            return new ApiResponse(404, "{\"error\":\"Endpoint not found: " + escapeJson(path) + "\"}");

        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.serverError("Internal Server Exception: " + e.getMessage());
        }
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }

    private static Map<String, String> parseJsonOrForm(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isEmpty()) return map;

        // Simple JSON extractor for key-values: "key": "val" or "key": 123
        if (body.startsWith("{")) {
            String stripped = body.substring(1, body.length() - 1).trim();
            String[] tokens = stripped.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
            for (String token : tokens) {
                String[] kv = token.split(":(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                if (kv.length == 2) {
                    String k = kv[0].trim().replace("\"", "");
                    String v = kv[1].trim().replace("\"", "");
                    map.put(k, v);
                }
            }
        } else {
            // URL Encoded form: a=b&c=d
            String[] pairs = body.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=");
                if (kv.length == 2) {
                    map.put(kv[0].trim(), kv[1].trim());
                }
            }
        }
        return map;
    }

    private static int parseInt(String val, int defaultVal) {
        if (val == null) return defaultVal;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
