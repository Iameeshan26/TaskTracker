package com.tasktracker.model;

public record Task(long id, String owner, String title, String description, String status) {}
