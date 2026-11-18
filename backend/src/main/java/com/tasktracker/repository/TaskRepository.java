package com.tasktracker.repository;

import com.tasktracker.exception.ApiException;
import com.tasktracker.model.Task;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;
import org.springframework.stereotype.Repository;

@Repository
public class TaskRepository {
  private final ConcurrentHashMap<Long, Task> tasks = new ConcurrentHashMap<>();
  private final AtomicLong sequence = new AtomicLong();

  public TaskRepository() {
    create(
        "alice",
        "Plan the next sprint",
        "Outline priorities and agree on a clear definition of done.");
    Task a =
        create(
            "alice", "Review the onboarding flow", "Make the first five minutes feel effortless.");
    update(a.id(), t -> new Task(t.id(), t.owner(), t.title(), t.description(), "in_progress"));
    create("bob", "Refresh the project documentation", "Capture setup steps and useful examples.");
    Task b = create("bob", "Share the weekly update", "Celebrate progress with the team.");
    update(b.id(), t -> new Task(t.id(), t.owner(), t.title(), t.description(), "done"));
  }

  public List<Task> all() {
    return tasks.values().stream()
        .sorted(Comparator.comparing(Task::owner).thenComparingLong(Task::id))
        .toList();
  }

  public Task find(long id) {
    Task t = tasks.get(id);
    if (t == null) throw ApiException.missing();
    return t;
  }

  public Task create(String owner, String title, String description) {
    long id = sequence.incrementAndGet();
    Task t = new Task(id, owner, title, description, "todo");
    tasks.put(id, t);
    return t;
  }

  public Task update(long id, UnaryOperator<Task> operation) {
    return tasks.compute(
        id,
        (key, t) -> {
          if (t == null) throw ApiException.missing();
          return operation.apply(t);
        });
  }

  public void delete(long id, UnaryOperator<Task> authorize) {
    tasks.compute(
        id,
        (key, t) -> {
          if (t == null) throw ApiException.missing();
          authorize.apply(t);
          return null;
        });
  }
}
