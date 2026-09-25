package servlet;

import controller.ApiController;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * File Location: src/servlet/CloudShieldServlet.java
 * Short Purpose: Java Servlet mapping HTTP requests in Apache Tomcat / Eclipse Dynamic Web Project.
 * Connections:
 *   - Mapped to URL pattern /api/* via @WebServlet annotation or web.xml.
 *   - Delegates incoming GET, POST, PUT, DELETE requests to ApiController.
 *   - Allows CloudShield to be deployed as a standard WAR on Apache Tomcat 10+.
 */
@WebServlet(name = "CloudShieldServlet", urlPatterns = {"/api/*"})
public class CloudShieldServlet extends HttpServlet {

    private final ApiController apiController = new ApiController();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processRequest(req, resp);
    }

    private void processRequest(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String method = req.getMethod();
        String path = req.getRequestURI();
        
        // Trim context path if present (e.g. /CloudShield/api/... -> /api/...)
        String contextPath = req.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        // Extract query parameters
        Map<String, String> queryParams = new HashMap<>();
        Map<String, String[]> rawParams = req.getParameterMap();
        for (Map.Entry<String, String[]> entry : rawParams.entrySet()) {
            if (entry.getValue() != null && entry.getValue().length > 0) {
                queryParams.put(entry.getKey(), entry.getValue()[0]);
            }
        }

        // Extract headers
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = req.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.put(name.toLowerCase(), req.getHeader(name));
        }

        String clientIp = req.getRemoteAddr();

        ApiController.ApiResponse apiResp = apiController.handleRequest(
            method, path, queryParams, headers, req.getInputStream(), clientIp
        );

        resp.setStatus(apiResp.statusCode);
        resp.setContentType(apiResp.contentType);
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(apiResp.body);
    }
}
