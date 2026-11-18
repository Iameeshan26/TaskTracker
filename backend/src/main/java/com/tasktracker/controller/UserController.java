package com.tasktracker.controller;

import com.tasktracker.repository.UserDirectory;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
  private final UserDirectory users;

  public UserController(UserDirectory users) {
    this.users = users;
  }

  @GetMapping
  List<UserDirectory.Summary> list(Authentication auth) {
    return users.list(auth);
  }
}
