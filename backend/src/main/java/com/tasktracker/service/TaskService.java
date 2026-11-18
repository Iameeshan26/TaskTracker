package com.tasktracker.service;
import com.tasktracker.model.Task;
import com.tasktracker.repository.TaskRepository;
import com.tasktracker.dto.CreateTask;
import com.tasktracker.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class TaskService {
 private final TaskRepository repository;
 public TaskService(TaskRepository repository) {this.repository=repository;}
 private boolean allowed(Task t,Authentication a) {return t.owner().equals(a.getName()) || a.getAuthorities().stream().anyMatch(r->r.getAuthority().equals("ROLE_ADMIN"));}
 private Task check(Task t,Authentication a) {if(!allowed(t,a))throw ApiException.missing();return t;}
 public void status(String status) {if(!Set.of("todo","in_progress","done").contains(status))throw ApiException.invalid("Invalid status");}
 public List<Task> list(Authentication a,String status) {if(status!=null)status(status);return repository.all().stream().filter(t->allowed(t,a)).filter(t->status==null||t.status().equals(status)).toList();}
 public Task get(long id,Authentication a) {return check(repository.find(id),a);}
 public Task create(CreateTask request,Authentication a) {String title=request.title().strip();if(title.length()<3)throw ApiException.invalid("Title must contain between 3 and 100 characters");return repository.create(a.getName(),title,request.description());}
 public Task patch(long id,JsonNode fields,Authentication a) {
  get(id,a);
  if(!fields.isObject() || fields.isEmpty())throw ApiException.invalid("Provide at least one field");
  fields.fieldNames().forEachRemaining(k->{if(!Set.of("title","description","status").contains(k))throw ApiException.invalid("Unknown field: "+k);});
  if(fields.has("title")) {JsonNode v=fields.get("title");if(!v.isTextual()||v.asText().strip().length()<3||v.asText().length()>100)throw ApiException.invalid("Title must contain between 3 and 100 characters");}
  if(fields.has("description")) {JsonNode v=fields.get("description");if(!v.isNull()&&(!v.isTextual()||v.asText().length()>500))throw ApiException.invalid("Description must be at most 500 characters");}
  if(fields.has("status")) {if(!fields.get("status").isTextual())throw ApiException.invalid("Invalid status");status(fields.get("status").asText());}
  return repository.update(id,t->{check(t,a);return new Task(t.id(),t.owner(),fields.has("title")?fields.get("title").asText().strip():t.title(),fields.has("description")?(fields.get("description").isNull()?null:fields.get("description").asText()):t.description(),fields.has("status")?fields.get("status").asText():t.status());});
 }
 public void delete(long id,Authentication a) {repository.delete(id,t->check(t,a));}
}
