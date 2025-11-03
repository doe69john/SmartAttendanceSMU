package com.smartattendance.supabase.web;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.smartattendance.supabase.auth.AccountAlreadyExistsException;
import com.smartattendance.supabase.auth.SupabaseAuthException;

@RestControllerAdvice(assignableTypes = SupabaseAuthController.class)
public class SupabaseAuthExceptionHandler {

    @ExceptionHandler(SupabaseAuthException.class)
    public ResponseEntity<Map<String, Object>> handleSupabaseAuthException(SupabaseAuthException exception) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", exception.getMessage());
        return ResponseEntity.status(exception.getStatus()).body(body);
    }

    @ExceptionHandler(AccountAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleAccountAlreadyExists(AccountAlreadyExistsException exception) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", exception.getMessage());
        return ResponseEntity.status(exception.getStatus()).body(body);
    }
}
