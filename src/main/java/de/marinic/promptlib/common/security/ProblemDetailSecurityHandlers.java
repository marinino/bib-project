package de.marinic.promptlib.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * A request rejected by the security filter chain (missing/invalid token, or authenticated but
 * not authorized) never reaches a controller, so GlobalExceptionHandler's @RestControllerAdvice
 * never runs for it - Spring Security's own default here is a bare response.sendError(), which
 * (confirmed by curling the running app) comes back with Content-Length: 0 and no body at all.
 * These two handlers write the same ProblemDetail JSON shape the rest of the API uses, so a 401
 * looks the same whether it came from a controller or from the filter chain.
 */
@Component
public class ProblemDetailSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemDetailSecurityHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        writeProblem(request, response, HttpStatus.UNAUTHORIZED, "Full authentication is required");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        writeProblem(request, response, HttpStatus.FORBIDDEN, "Access to this resource is denied");
    }

    private void writeProblem(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));

        // Without an explicit charset, the servlet container's default (ISO-8859-1) wins over
        // response.getWriter() - confirmed live via curl, showed up as
        // "application/problem+json;charset=ISO-8859-1" instead of matching the UTF-8 the
        // regular controller path implicitly uses through Spring's own message converters.
        response.setStatus(status.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/problem+json");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
