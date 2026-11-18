package com.tasktracker.exception;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice
public class ApiErrors {
 @ExceptionHandler(ApiException.class) ResponseEntity<?> api(ApiException e) { return ResponseEntity.status(e.status).body(Map.of("error",e.code,"message",e.getMessage())); }
 @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class}) ResponseEntity<?> invalid(Exception e) {return ResponseEntity.badRequest().body(Map.of("error","VALIDATION_ERROR","message","Check your input. Title: 3–100 characters; description: up to 500; status: todo, in_progress or done. Unknown fields are not allowed."));}
}
