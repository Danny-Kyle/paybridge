package com.academy.paybridge.shared.security;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
@Component
public class CurrentActor {
 public Jwt jwt() {
  var a=SecurityContextHolder.getContext().getAuthentication();
  if(a==null || !(a.getPrincipal() instanceof Jwt j)) throw new AccessDeniedException("A customer access token is required");
  return j;
 }
 public String subject(){return jwt().getSubject();}
 public String issuer(){return jwt().getIssuer().toString();}
 public boolean hasRole(String role){
  var a=SecurityContextHolder.getContext().getAuthentication();
  return a!=null && a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_"+role));
 }
 public void requireRole(String role){if(!hasRole(role))throw new AccessDeniedException("Required role: "+role);}
}
