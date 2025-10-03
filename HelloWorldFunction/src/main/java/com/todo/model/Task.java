package com.todo.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a Task stored in DynamoDB.
 * Fields match the attributes in the SAM template.
 */
public class Task {

    private String taskId;
    private String userId;
    private String description;
    private String status;
    private Long deadline;
    private Long expireAt;

    // Default constructor (needed for DynamoDB / JSON serialization)
    public Task() {}

    // Constructor for new tasks
    public Task(String userId, String description) {
        this.taskId = UUID.randomUUID().toString();
        this.userId = userId;
        this.description = description;
        this.status = "Pending";

        // Deadline = 5 minutes from now
        this.deadline = Instant.now().plusSeconds(300).toEpochMilli();

        // ExpireAt used for DynamoDB TTL (optional but helpful for cleanup)
        this.expireAt = this.deadline / 1000; // TTL requires seconds, not millis
    }

    // --- Getters and Setters ---
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getDeadline() { return deadline; }
    public void setDeadline(Long deadline) { this.deadline = deadline; }

    public Long getExpireAt() { return expireAt; }
    public void setExpireAt(Long expireAt) { this.expireAt = expireAt; }
}
