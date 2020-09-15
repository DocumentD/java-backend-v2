package de.skillkiller.documentdbackend;

import de.skillkiller.documentdbackend.filter.AuthorizationFilter;
import de.skillkiller.documentdbackend.service.UserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@EnableWebSecurity
public class SecurityConfigurer extends WebSecurityConfigurerAdapter {

    private final UserDetailsService userDetailsService;
    private final AuthorizationFilter authorizationFilter;

    public SecurityConfigurer(UserDetailsService userDetailsService, AuthorizationFilter authorizationFilter) {
        this.userDetailsService = userDetailsService;
        this.authorizationFilter = authorizationFilter;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.cors().and().csrf().disable()
                .authorizeRequests().antMatchers("/**/open/*/*").permitAll()
                .and()
                .authorizeRequests().antMatchers("/login").permitAll()
                .and()
                .authorizeRequests().anyRequest().authenticated()
                .and()
                .sessionManagement(configurer -> configurer.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.addFilterBefore(authorizationFilter, UsernamePasswordAuthenticationFilter.class);

    }

    @Override
    protected org.springframework.security.core.userdetails.UserDetailsService userDetailsService() {
        return userDetailsService;
    }

    @Override
    @Bean
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder();
    }
}
