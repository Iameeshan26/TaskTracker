package com.tasktracker.repository;

import com.tasktracker.exception.ApiException;
import com.tasktracker.service.TaskPermissions;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.*;

/** Immutable account directory shared by authentication and task assignment. */
public final class UserDirectory implements UserDetailsService {
  public record Summary(String username, String role) {}

  private final Map<String, UserDetails> accounts;

  public UserDirectory(List<UserDetails> users) {
    accounts =
        users.stream().collect(Collectors.toUnmodifiableMap(UserDetails::getUsername, u -> u));
  }

  @Override
  public UserDetails loadUserByUsername(String username) {
    UserDetails user = accounts.get(username);
    if (user == null) throw new UsernameNotFoundException("Invalid credentials");
    // Authentication erases credentials from its principal; keep the stored hash intact.
    return User.withUserDetails(user).build();
  }

  public List<Summary> list(Authentication auth) {
    if (!TaskPermissions.canManage(auth))
      throw new ApiException(
          403, "FORBIDDEN", "Only admins and managers can view the user directory");
    return accounts.values().stream()
        .filter(UserDetails::isEnabled)
        .map(
            u ->
                new Summary(
                    u.getUsername(),
                    u.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "")))
        .sorted(Comparator.comparing(Summary::username))
        .toList();
  }

  public String assignee(String username, Authentication auth) {
    if (!TaskPermissions.canManage(auth))
      throw new ApiException(403, "FORBIDDEN", "Only admins and managers can assign tasks");
    if (username == null || !accounts.containsKey(username) || !accounts.get(username).isEnabled())
      throw ApiException.invalid("Select an existing user to assign this task");
    return username;
  }
}
