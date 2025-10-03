package com.todo.functions.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Lambda handler for processing DynamoDB Stream events.
 * When a task is created or updated, push a message to SQS if Deadline exists.
 */
public class StreamProcessorHandler implements RequestHandler<DynamodbEvent, Void> {

    private final SqsClient sqsClient = SqsClient.create();
    private final String queueUrl = System.getenv("TASK_EXPIRY_QUEUE_URL");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Void handleRequest(DynamodbEvent event, Context context) {
        event.getRecords().forEach(record -> {
            if ("INSERT".equals(record.getEventName()) || "MODIFY".equals(record.getEventName())) {
                try {
                    Map<String, com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue> newImage =
                            record.getDynamodb().getNewImage();

                    String taskId = newImage.get("TaskId").getS();
                    String userId = newImage.get("UserId").getS();

                    // Use Deadline instead of DueDate (numeric epoch value)
                    String deadlineStr = newImage.containsKey("Deadline")
                            ? newImage.get("Deadline").getN()
                            : null;

                    if (deadlineStr != null) {
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("taskId", taskId);
                        payload.put("userId", userId);
                        payload.put("deadline", deadlineStr);

                        String message = objectMapper.writeValueAsString(payload);

                        sqsClient.sendMessage(SendMessageRequest.builder()
                                .queueUrl(queueUrl)
                                .messageBody(message)
                                // Delay until deadline (max 15 minutes for SQS)
                                .delaySeconds(calculateDelaySeconds(deadlineStr))
                                .build());

                        context.getLogger().log("Pushed task to SQS: " + message);
                    }
                } catch (Exception e) {
                    context.getLogger().log("Error processing stream record: " + e.getMessage());
                }
            }
        });
        return null;
    }

    /**
     * Calculate delay (seconds) between now and deadline.
     * If deadline already passed, return 0 for immediate processing.
     */
    private int calculateDelaySeconds(String deadlineStr) {
        try {
            long deadlineEpoch = Long.parseLong(deadlineStr);
            long diff = deadlineEpoch - Instant.now().getEpochSecond();
            return (int) Math.max(0, Math.min(diff, 900)); // SQS max delay = 15 min
        } catch (Exception e) {
            return 0;
        }
    }
}
