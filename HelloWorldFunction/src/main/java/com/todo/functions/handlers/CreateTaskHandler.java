package com.todo.functions.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.model.Task;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Lambda handler for creating a new Task.
 * Triggered by POST /tasks
 */
public class CreateTaskHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
    private final String tableName = System.getenv("TABLE_NAME");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            // Deserialize request body into Task (only description is expected from client)
            Map<String, Object> body = objectMapper.readValue(request.getBody(), Map.class);
            String description = (String) body.get("description");

            // Get UserId from Cognito authorizer claims
            String userId = request.getRequestContext().getAuthorizer().get("claims") != null
                    ? (String) ((Map<String, Object>) request.getRequestContext().getAuthorizer().get("claims")).get("sub")
                    : "anonymous"; // fallback (useful for local testing)

            // Create Task object
            Task task = new Task(userId, description);

            // Build DynamoDB item
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("UserId", AttributeValue.builder().s(task.getUserId()).build());
            item.put("TaskId", AttributeValue.builder().s(task.getTaskId()).build());
            item.put("Description", AttributeValue.builder().s(task.getDescription()).build());
            item.put("Status", AttributeValue.builder().s(task.getStatus()).build());
            item.put("Deadline", AttributeValue.builder().n(task.getDeadline().toString()).build());
            item.put("ExpireAt", AttributeValue.builder().n(task.getExpireAt().toString()).build());

            // Save to DynamoDB
            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build());

            // Return success response
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(201)
                    .withBody(objectMapper.writeValueAsString(task));

        } catch (Exception e) {
            context.getLogger().log("Error in CreateTaskHandler: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"Could not create task\"}");
        }
    }
}
