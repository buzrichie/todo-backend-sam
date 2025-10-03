package com.todo.functions.expiry;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Lambda handler for consuming SQS messages and marking tasks as expired.
 * Also notifies users via SNS.
 */
public class TaskExpiryHandler implements RequestHandler<SQSEvent, Void> {

    private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
    private final SnsClient snsClient = SnsClient.create();
    private final String tableName = System.getenv("TABLE_NAME");
    private final String topicArn = System.getenv("NOTIFICATION_TOPIC_ARN");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        event.getRecords().forEach(message -> {
            try {
                Map<String, Object> payload = objectMapper.readValue(message.getBody(), Map.class);
                String userId = (String) payload.get("userId");
                String taskId = (String) payload.get("taskId");
                String dueDate = (String) payload.get("dueDate");

                // Update DynamoDB status → EXPIRED
                Map<String, AttributeValue> key = new HashMap<>();
                key.put("UserId", AttributeValue.builder().s(userId).build());
                key.put("TaskId", AttributeValue.builder().s(taskId).build());

                dynamoDbClient.updateItem(UpdateItemRequest.builder()
                        .tableName(tableName)
                        .key(key)
                        .updateExpression("SET #st = :expired")
                        .expressionAttributeNames(Map.of("#st", "Status"))
                        .expressionAttributeValues(Map.of(":expired", AttributeValue.builder().s("EXPIRED").build()))
                        .build());

                // Publish notification to SNS
                String msg = String.format("Task %s has expired! Due: %s", taskId, dueDate);
                snsClient.publish(PublishRequest.builder()
                        .topicArn(topicArn)
                        .subject("Task Expired")
                        .message(msg)
                        .build());

                context.getLogger().log("Task marked expired and notification sent: " + taskId);

            } catch (Exception e) {
                context.getLogger().log("Error in TaskExpiryHandler: " + e.getMessage());
            }
        });
        return null;
    }
}
