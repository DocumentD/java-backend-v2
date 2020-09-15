package de.skillkiller.documentdbackend.filter;

import com.auth0.jwt.interfaces.DecodedJWT;
import de.skillkiller.documentdbackend.entity.User;
import de.skillkiller.documentdbackend.entity.UserDetailsHolder;
import de.skillkiller.documentdbackend.service.UserDetailsService;
import de.skillkiller.documentdbackend.util.JWTUtil;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;

@Component
public class AuthorizationFilter extends OncePerRequestFilter {

    private final JWTUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public AuthorizationFilter(JWTUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, FilterChain filterChain) throws ServletException, IOException {
        final String authorizationHeader = httpServletRequest.getHeader("Authorization");

        String userid;
        String jwtToken;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwtToken = authorizationHeader.substring(7);
            DecodedJWT decodedJWT = jwtUtil.validateToken(jwtToken);
            if (decodedJWT != null) {
                userid = decodedJWT.getSubject();
                if (userid != null) {
                    UserDetails userDetails = this.userDetailsService.loadUserById(userid);
                    User user = ((UserDetailsHolder) userDetails).getAuthenticatedUser();

                    if (user.getModifyDate().before(decodedJWT.getIssuedAt()) && decodedJWT.getExpiresAt().after(new Date())) {
                        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities()
                        );
                        usernamePasswordAuthenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(httpServletRequest));
                        SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
                    }
                }
            }
        }

        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }
}
