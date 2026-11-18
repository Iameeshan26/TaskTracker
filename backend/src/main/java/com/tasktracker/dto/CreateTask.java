package com.tasktracker.dto;

import jakarta.validation.constraints.*;

public record CreateTask(
    @NotBlank @Size(min = 3, max = 100) String title, @Size(max = 500) String description) {}
