package com.academy.paybridge.shared.security;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
 @Bean SecurityFilterChain security(HttpSecurity http, Environment env) throws Exception {
  // Bearer tokens only: no cookie-authenticated API sessions.
  http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
  http.authorizeHttpRequests(a->{
   a.requestMatchers("/actuator/health","/error","/api/v1/webhooks/paystack").permitAll();
   if(env.acceptsProfiles(org.springframework.core.env.Profiles.of("local","test")))
    a.requestMatchers("/swagger-ui/**","/swagger-ui.html","/v3/api-docs/**").permitAll();
   a.requestMatchers("/api/v1/settlements/**").hasRole("TREASURY");
   a.requestMatchers("/api/v1/local/**").hasRole("TREASURY");
   a.anyRequest().authenticated();
  });
  var converter=new JwtAuthenticationConverter();
  converter.setJwtGrantedAuthoritiesConverter(jwt->{
   var result=new ArrayList<org.springframework.security.core.GrantedAuthority>();
   result.addAll(new JwtGrantedAuthoritiesConverter().convert(jwt));
   Object realm=jwt.getClaim("realm_access");
   if(realm instanceof Map<?,?> m && m.get("roles") instanceof Collection<?> roles)
    roles.stream().filter(String.class::isInstance).map(String.class::cast)
     .filter(Set.of("CUSTOMER","TREASURY","COMPLIANCE","SUPPORT")::contains)
     .forEach(r->result.add(new SimpleGrantedAuthority("ROLE_"+r)));
   return result;
  });
  http.oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(converter)));
  return http.build();
 }
}
