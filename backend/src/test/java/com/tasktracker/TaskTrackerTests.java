package com.tasktracker;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import com.tasktracker.repository.TaskRepository;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;

@SpringBootTest
@AutoConfigureMockMvc
class TaskTrackerTests {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired TaskRepository repository;
  @Autowired UserDetailsService users;
  @Autowired PasswordEncoder encoder;
  long alice, bob;

  @BeforeEach
  void fixtures() {
    repository.all().forEach(t -> repository.delete(t.id(), x -> x));
    alice = repository.create("alice", "Alice fixture", "Original description").id();
    bob = repository.create("bob", "Bob fixture", "Private description").id();
  }

  ResultActions call(String method, String path, String body, String username) throws Exception {
    var builder =
        switch (method) {
          case "POST" -> post(path);
          case "PATCH" -> patch(path);
          case "DELETE" -> delete(path);
          default -> get(path);
        };
    if (username != null)
      builder.with(
          user(username)
              .roles(
                  username.equals("admin")
                      ? "ADMIN"
                      : username.equals("manager") ? "MANAGER" : "USER"));
    if (!method.equals("GET")) builder.with(csrf());
    if (body != null) builder.contentType("application/json").content(body);
    return mvc.perform(builder);
  }

  MockHttpSession login(String username, String password) throws Exception {
    return (MockHttpSession)
        mvc.perform(
                post("/api/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content(
                        json.writeValueAsString(
                            Map.of("username", username, "password", password))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("username").value(username))
            .andReturn()
            .getRequest()
            .getSession();
  }

  @ParameterizedTest
  @CsvSource({
    "alice,Alice-demo-2026!,USER",
    "admin,Admin-demo-2026!,ADMIN",
    "bob,Bob-demo-2026!,USER",
    "manager,Manager-demo-2026!,MANAGER"
  })
  void validLogin(String name, String password, String role) throws Exception {
    var session = login(name, password);
    mvc.perform(get("/api/me").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("role").value(role));
  }

  @ParameterizedTest
  @CsvSource({"alice,wrong", "unknown,wrong"})
  void invalidLogin(String name, String password) throws Exception {
    mvc.perform(
            post("/api/login")
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", name, "password", password))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("message").value("Invalid username or password"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"GET", "POST", "PATCH", "DELETE"})
  void unauthenticated(String method) throws Exception {
    call(
            method,
            method.equals("PATCH") || method.equals("DELETE") ? "/api/tasks/1" : "/api/tasks",
            "{}",
            null)
        .andExpect(status().isUnauthorized());
  }

  @ParameterizedTest
  @ValueSource(strings = {"POST", "PATCH", "DELETE"})
  void unauthenticatedWithoutCsrf(String method) throws Exception {
    var r =
        method.equals("POST")
            ? post("/api/tasks")
            : method.equals("PATCH") ? patch("/api/tasks/1") : delete("/api/tasks/1");
    mvc.perform(r).andExpect(status().isUnauthorized());
  }

  @Test
  void logoutInvalidatesSession() throws Exception {
    var s = login("alice", "Alice-demo-2026!");
    mvc.perform(post("/api/logout").session(s).with(csrf())).andExpect(status().isNoContent());
    assertTrue(s.isInvalid());
    mvc.perform(get("/api/tasks").cookie(new jakarta.servlet.http.Cookie("JSESSIONID", s.getId())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void sessionFixationAndRealCsrf() throws Exception {
    var tokenResponse = mvc.perform(get("/api/csrf")).andExpect(status().isOk()).andReturn();
    var s = (MockHttpSession) tokenResponse.getRequest().getSession();
    String old = s.getId();
    String token =
        json.readTree(tokenResponse.getResponse().getContentAsString()).get("token").asText();
    mvc.perform(
            post("/api/login")
                .session(s)
                .header("X-CSRF-TOKEN", token)
                .contentType("application/json")
                .content("{\"username\":\"alice\",\"password\":\"Alice-demo-2026!\"}"))
        .andExpect(status().isOk());
    assertNotEquals(old, s.getId());
    mvc.perform(
            post("/api/tasks")
                .session(s)
                .header("X-CSRF-TOKEN", token)
                .contentType("application/json")
                .content("{\"title\":\"New task\"}"))
        .andExpect(status().isForbidden());
    var fresh = mvc.perform(get("/api/csrf").session(s)).andReturn();
    token = json.readTree(fresh.getResponse().getContentAsString()).get("token").asText();
    mvc.perform(
            post("/api/tasks")
                .session(s)
                .header("X-CSRF-TOKEN", token)
                .contentType("application/json")
                .content("{\"title\":\"New task\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void aliceOnlySeesOwnTasks() throws Exception {
    var result = call("GET", "/api/tasks", null, "alice").andExpect(status().isOk()).andReturn();
    var tasks = json.readTree(result.getResponse().getContentAsString());
    assertTrue(tasks.size() > 0);
    tasks.forEach(t -> assertEquals("alice", t.get("owner").asText()));
  }

  @Test
  void bobOnlySeesOwnTasks() throws Exception {
    var r = call("GET", "/api/tasks", null, "bob").andReturn();
    json.readTree(r.getResponse().getContentAsString())
        .forEach(t -> assertEquals("bob", t.get("owner").asText()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"GET", "PATCH", "DELETE"})
  void crossUserIs404(String method) throws Exception {
    call(method, "/api/tasks/" + bob, "{\"title\":\"Changed\"}", "alice")
        .andExpect(status().isNotFound());
  }

  @Test
  void adminSeesOwnersAndStableOrder() throws Exception {
    String first =
        call("GET", "/api/tasks", null, "admin")
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertEquals(
        first,
        call("GET", "/api/tasks", null, "admin").andReturn().getResponse().getContentAsString());
    Set<String> owners = new HashSet<>();
    json.readTree(first).forEach(t -> owners.add(t.get("owner").asText()));
    assertTrue(owners.containsAll(Set.of("alice", "bob")));
  }

  @ParameterizedTest
  @ValueSource(strings = {"alice", "bob"})
  void adminCanUpdateAnyOwner(String owner) throws Exception {
    call(
            "PATCH",
            "/api/tasks/" + (owner.equals("alice") ? alice : bob),
            "{\"status\":\"done\"}",
            "admin")
        .andExpect(status().isOk())
        .andExpect(jsonPath("owner").value(owner))
        .andExpect(jsonPath("status").value("done"));
  }

  @Test
  void adminCanDeleteOthers() throws Exception {
    call("DELETE", "/api/tasks/" + bob, null, "admin").andExpect(status().isNoContent());
    call("GET", "/api/tasks/" + bob, null, "admin").andExpect(status().isNotFound());
  }

  @Test
  void createIsOwnedAndTodo() throws Exception {
    call("POST", "/api/tasks", "{\"title\":\"New task\",\"description\":\"Context\"}", "alice")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("owner").value("alice"))
        .andExpect(jsonPath("status").value("todo"))
        .andExpect(jsonPath("id").isNumber());
  }

  @Test
  void ownUpdatePreservesOmittedFields() throws Exception {
    call("PATCH", "/api/tasks/" + alice, "{\"status\":\"in_progress\"}", "alice")
        .andExpect(status().isOk())
        .andExpect(jsonPath("title").value("Alice fixture"))
        .andExpect(jsonPath("description").value("Original description"));
  }

  @Test
  void ownDelete() throws Exception {
    call("DELETE", "/api/tasks/" + alice, null, "alice").andExpect(status().isNoContent());
    call("GET", "/api/tasks/" + alice, null, "alice").andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"title\":123}",
        "{\"title\":true}",
        "{\"title\":12.5}",
        "{\"title\":\"Valid\",\"description\":42}"
      })
  void numericStringsAreRejected(String payload) throws Exception {
    call("POST", "/api/tasks", payload, "alice").andExpect(status().isBadRequest());
  }

  static List<String> invalidCreates() {
    return List.of(
        "{}",
        "{\"title\":\"ab\"}",
        "{\"title\":\"   \"}",
        "{\"title\":null}",
        "{\"title\":\"" + "x".repeat(101) + "\"}",
        "{\"title\":\"Valid\",\"description\":\"" + "x".repeat(501) + "\"}",
        "{\"title\":\"Valid\",\"owner\":\"bob\"}",
        "{\"title\":\"Valid\",\"role\":\"ADMIN\"}",
        "{\"title\":\"Valid\",\"status\":\"done\"}",
        "{\"title\":\"Valid\",\"id\":1}");
  }

  @ParameterizedTest
  @MethodSource("invalidCreates")
  void invalidCreate(String body) throws Exception {
    call("POST", "/api/tasks", body, "alice")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("error").value("VALIDATION_ERROR"));
  }

  static List<String> invalidPatches() {
    return List.of(
        "{}",
        "null",
        "[]",
        "{\"title\":null}",
        "{\"title\":\"ab\"}",
        "{\"title\":\"   \"}",
        "{\"title\":\"" + "x".repeat(101) + "\"}",
        "{\"description\":\"" + "x".repeat(501) + "\"}",
        "{\"description\":123}",
        "{\"status\":\"other\"}",
        "{\"status\":null}",
        "{\"owner\":\"bob\"}",
        "{\"role\":\"ADMIN\"}",
        "{\"id\":8}");
  }

  @ParameterizedTest
  @MethodSource("invalidPatches")
  void invalidPatchAppliesToAdminToo(String body) throws Exception {
    call("PATCH", "/api/tasks/" + alice, body, "admin").andExpect(status().isBadRequest());
    assertEquals("Alice fixture", repository.find(alice).title());
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "\"\""})
  void clearDescription(String value) throws Exception {
    call("PATCH", "/api/tasks/" + alice, "{\"description\":" + value + "}", "alice")
        .andExpect(status().isOk());
    assertTrue(
        repository.find(alice).description() == null
            || repository.find(alice).description().isEmpty());
  }

  @Test
  void validBoundaryValues() throws Exception {
    call(
            "POST",
            "/api/tasks",
            json.writeValueAsString(
                Map.of("title", "x".repeat(100), "description", "x".repeat(500))),
            "alice")
        .andExpect(status().isCreated());
  }

  @Test
  void invalidFilter() throws Exception {
    call("GET", "/api/tasks?status=invalid", null, "alice").andExpect(status().isBadRequest());
  }

  @Test
  void filteringRespectsOwnership() throws Exception {
    call("PATCH", "/api/tasks/" + alice, "{\"status\":\"done\"}", "alice");
    var r =
        call("GET", "/api/tasks?status=done", null, "alice").andExpect(status().isOk()).andReturn();
    json.readTree(r.getResponse().getContentAsString())
        .forEach(
            t -> {
              assertEquals("alice", t.get("owner").asText());
              assertEquals("done", t.get("status").asText());
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"GET", "PATCH", "DELETE"})
  void missingTask(String method) throws Exception {
    call(method, "/api/tasks/9223372036854775807", "{\"title\":\"Valid\"}", "alice")
        .andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @ValueSource(strings = {"abc", "999999999999999999999999"})
  void invalidId(String id) throws Exception {
    call("PATCH", "/api/tasks/" + id, "{\"title\":\"Valid\"}", "alice")
        .andExpect(status().isBadRequest());
  }

  @Test
  void hashesOnly() {
    for (String name : List.of("alice", "bob", "admin", "manager")) {
      String hash = users.loadUserByUsername(name).getPassword();
      assertTrue(hash.startsWith("$2a$"));
      assertTrue(
          encoder.matches(
              name.substring(0, 1).toUpperCase() + name.substring(1) + "-demo-2026!", hash));
    }
  }

  @Test
  void responsesNeverExposePasswords() throws Exception {
    var s = login("alice", "Alice-demo-2026!");
    String result =
        mvc.perform(get("/api/me").session(s)).andReturn().getResponse().getContentAsString();
    assertFalse(result.contains("password"));
    assertFalse(result.contains("$2a$"));
    assertEquals(2, json.readTree(result).size());
  }

  @Test
  void csrfRequiredForAuthenticatedMutation() throws Exception {
    mvc.perform(
            post("/api/tasks")
                .with(user("alice"))
                .contentType("application/json")
                .content("{\"title\":\"Valid\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void loginRequiresCsrf() throws Exception {
    mvc.perform(
            post("/api/login")
                .contentType("application/json")
                .content("{\"username\":\"alice\",\"password\":\"Alice-demo-2026!\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logoutRequiresCsrf() throws Exception {
    var s = login("alice", "Alice-demo-2026!");
    mvc.perform(post("/api/logout").session(s)).andExpect(status().isForbidden());
    mvc.perform(get("/api/me").session(s)).andExpect(status().isOk());
  }

  @ParameterizedTest
  @ValueSource(strings = {"admin", "manager"})
  void directoryContainsOnlySafeAccountSummaries(String actor) throws Exception {
    var response = call("GET", "/api/users", null, actor).andExpect(status().isOk()).andReturn();
    var body = json.readTree(response.getResponse().getContentAsString());
    assertEquals(
        List.of("admin", "alice", "bob", "manager"),
        json.convertValue(body.findValues("username"), List.class));
    assertEquals("MANAGER", body.get(3).get("role").asText());
    body.forEach(
        account -> {
          assertEquals(2, account.size());
          assertFalse(account.has("password"));
        });
  }

  @Test
  void directoryIsRestricted() throws Exception {
    call("GET", "/api/users", null, null).andExpect(status().isUnauthorized());
    call("GET", "/api/users", null, "alice").andExpect(status().isForbidden());
  }

  @ParameterizedTest
  @ValueSource(strings = {"admin", "manager"})
  void leadersCreateTasksForUsers(String actor) throws Exception {
    var response =
        call("POST", "/api/tasks", "{\"title\":\"Assigned task\",\"assignee\":\"alice\"}", actor)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("owner").value("alice"))
            .andExpect(jsonPath("status").value("todo"))
            .andReturn();
    long id = json.readTree(response.getResponse().getContentAsString()).get("id").asLong();
    call("GET", "/api/tasks/" + id, null, "alice").andExpect(status().isOk());
    call("GET", "/api/tasks/" + id, null, "bob").andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @ValueSource(strings = {"admin", "manager"})
  void reassignmentMovesAccessAndPreservesOtherFields(String actor) throws Exception {
    call("PATCH", "/api/tasks/" + alice, "{\"assignee\":\"bob\"}", actor)
        .andExpect(status().isOk())
        .andExpect(jsonPath("owner").value("bob"))
        .andExpect(jsonPath("title").value("Alice fixture"))
        .andExpect(jsonPath("description").value("Original description"))
        .andExpect(jsonPath("status").value("todo"));
    call("GET", "/api/tasks/" + alice, null, "alice").andExpect(status().isNotFound());
    call("PATCH", "/api/tasks/" + alice, "{\"status\":\"done\"}", "alice")
        .andExpect(status().isNotFound());
    call("GET", "/api/tasks/" + alice, null, "bob").andExpect(status().isOk());
    var list =
        call("GET", "/api/tasks", null, "alice").andReturn().getResponse().getContentAsString();
    assertEquals(0, json.readTree(list).size());
  }

  @ParameterizedTest
  @ValueSource(strings = {"POST", "PATCH"})
  void regularUsersCannotAssign(String method) throws Exception {
    call(
            method,
            method.equals("POST") ? "/api/tasks" : "/api/tasks/" + alice,
            "{\"title\":\"Unwanted change\",\"assignee\":\"bob\"}",
            "alice")
        .andExpect(status().isForbidden());
    assertEquals("Alice fixture", repository.find(alice).title());
    assertEquals("alice", repository.find(alice).owner());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"missing\"", "\"\"", "\"   \"", "null", "42", "true", "{}"})
  void invalidReassignmentIsAtomic(String value) throws Exception {
    call(
            "PATCH",
            "/api/tasks/" + alice,
            "{\"title\":\"Unwanted change\",\"assignee\":" + value + "}",
            "manager")
        .andExpect(status().isBadRequest());
    assertEquals("Alice fixture", repository.find(alice).title());
    assertEquals("alice", repository.find(alice).owner());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"missing\"", "\"\"", "\"   \"", "42", "true"})
  void invalidCreateAssigneeIsRejected(String value) throws Exception {
    call("POST", "/api/tasks", "{\"title\":\"Assigned task\",\"assignee\":" + value + "}", "admin")
        .andExpect(status().isBadRequest());
    assertEquals(2, repository.all().size());
  }

  @Test
  void managerCanManageTeamTasksButCannotOverrideProtectedFields() throws Exception {
    call("GET", "/api/tasks", null, "manager")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
    call("PATCH", "/api/tasks/" + alice, "{\"status\":\"done\"}", "manager")
        .andExpect(status().isOk());
    call("PATCH", "/api/tasks/" + bob, "{\"role\":\"ADMIN\"}", "manager")
        .andExpect(status().isBadRequest());
    call("PATCH", "/api/tasks/" + bob, "{\"owner\":\"alice\"}", "manager")
        .andExpect(status().isBadRequest());
    call(
            "POST",
            "/api/tasks",
            "{\"title\":\"Task\",\"status\":\"done\",\"assignee\":\"alice\"}",
            "manager")
        .andExpect(status().isBadRequest());
    call("DELETE", "/api/tasks/" + bob, null, "manager").andExpect(status().isNoContent());
  }

  @Test
  void managerCanKeepTaskInOwnWorkspace() throws Exception {
    call("POST", "/api/tasks", "{\"title\":\"Manager task\"}", "manager")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("owner").value("manager"));
  }

  @Test
  void successfulLoginDoesNotEraseStoredHashes() throws Exception {
    login("manager", "Manager-demo-2026!");
    login("manager", "Manager-demo-2026!");
    assertTrue(
        encoder.matches("Manager-demo-2026!", users.loadUserByUsername("manager").getPassword()));
  }
}
