package com.tasktracker.controller;
import com.tasktracker.dto.LoginRequest;
import com.tasktracker.exception.ApiException;
import jakarta.validation.Valid;
import jakarta.servlet.http.*;
import java.util.Map;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api")
public class AuthController {
 private final AuthenticationManager manager;private final HttpSessionSecurityContextRepository repository;
 public AuthController(AuthenticationManager manager,HttpSessionSecurityContextRepository repository) {this.manager=manager;this.repository=repository;}
 @GetMapping("/csrf") Map<String,String> csrf(CsrfToken token) {return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}
 @PostMapping("/login") Map<String,String> login(@Valid @RequestBody LoginRequest body,HttpServletRequest request,HttpServletResponse response) {
  Authentication auth;
  try {auth=manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(body.username(),body.password()));}
  catch(AuthenticationException e) {throw new ApiException(401,"INVALID_CREDENTIALS","Invalid username or password");}
  if(request.getSession(false)!=null)request.changeSessionId();
  new HttpSessionCsrfTokenRepository().saveToken(null,request,response);
  var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(auth);SecurityContextHolder.setContext(context);repository.saveContext(context,request,response);
  return identity(auth);
 }
 @GetMapping("/me") Map<String,String> identity(Authentication auth) {return Map.of("username",auth.getName(),"role",auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_",""));}
}
