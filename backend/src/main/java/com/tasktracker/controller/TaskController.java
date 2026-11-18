package com.tasktracker.controller;
import com.tasktracker.service.TaskService;
import com.tasktracker.dto.CreateTask;
import com.tasktracker.model.Task;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/tasks")
public class TaskController {
 private final TaskService service;public TaskController(TaskService service) {this.service=service;}
 @GetMapping List<Task> list(Authentication a,@RequestParam(required=false) String status) {return service.list(a,status);}
 @GetMapping("/{id}") Task get(@PathVariable long id,Authentication a) {return service.get(id,a);}
 @PostMapping ResponseEntity<Task> create(@Valid @RequestBody CreateTask body,Authentication a) {Task t=service.create(body,a);return ResponseEntity.status(201).body(t);}
 @PatchMapping("/{id}") Task patch(@PathVariable long id,@RequestBody JsonNode body,Authentication a) {return service.patch(id,body,a);}
 @DeleteMapping("/{id}") ResponseEntity<Void> delete(@PathVariable long id,Authentication a) {service.delete(id,a);return ResponseEntity.noContent().build();}
}
