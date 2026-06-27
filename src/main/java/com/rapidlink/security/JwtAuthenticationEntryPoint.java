package com.rapidlink.security;


import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import java.io.IOException;

/**
 * Handles authentication failures for protected resources.
 *
 * <p>Invoked when an unauthenticated request attempts to access
 * a secured endpoint.
 */
@Component
@Slf4j
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {

        String error = (String) request.getAttribute("jwt.error");

        String message = (error != null)
                ? error
                : "Authentication required";

        log.debug(
                "Authentication failed. uri={} reason={}",
                request.getRequestURI(),
                message
        );

        response.sendError(
                HttpServletResponse.SC_UNAUTHORIZED,
                message
        );
    }
}
