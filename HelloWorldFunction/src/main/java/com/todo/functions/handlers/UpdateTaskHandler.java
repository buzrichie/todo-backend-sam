package com.todo.functions.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Lambda handler for updating a Task.
 * Triggered by PUT /tasks/{taskId}
 */
public class UpdateTaskHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
    private final String tableName = System.getenv("TABLE_NAME");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String taskId = request.getPathParameters().get("taskId");

            // Get userId from Cognito claims safely
            Map<String, Object> authorizer = request.getRequestContext().getAuthorizer();
            String userId = "anonymous";
            if (authorizer != null && authorizer.get("claims") instanceof Map) {
                Map<String, Object> claims = (Map<String, Object>) authorizer.get("claims");
                userId = claims.getOrDefault("sub", "anonymous").toString();
            }

            // Parse body
            Map<String, Object> body = objectMapper.readValue(request.getBody(), Map.class);

            Map<String, String> expressionNames = new HashMap<>();
            StringBuilder updateExpr = new StringBuilder("SET ");
            Map<String, AttributeValue> expressionValues = new HashMap<>();

            if (body.containsKey("description")) {
                expressionNames.put("#desc", "Description");
                expressionValues.put(":desc", AttributeValue.builder().s(body.get("description").toString()).build());
                updateExpr.append("#desc = :desc, ");
            }

            if (body.containsKey("status")) {
                expressionNames.put("#st", "Status");
                expressionValues.put(":st", AttributeValue.builder().s(body.get("status").toString()).build());
                updateExpr.append("#st = :st, ");
            }

            // If no fields were provided, return 400
            if (expressionValues.isEmpty()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withBody("{\"error\":\"No valid fields provided for update\"}");
            }

            // Remove trailing comma
            String finalUpdateExpr = updateExpr.toString().replaceAll(", $", "");

            // Build key
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("UserId", AttributeValue.builder().s(userId).build());
            key.put("TaskId", AttributeValue.builder().s(taskId).build());

            // Execute update
            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .updateExpression(finalUpdateExpr)
                    .expressionAttributeNames(expressionNames)
                    .expressionAttributeValues(expressionValues)
                    .returnValues("UPDATED_NEW") // helpful for debugging
                    .build());

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody("{\"message\":\"Task updated successfully\"}");

        } catch (Exception e) {
            context.getLogger().log("Error in UpdateTaskHandler: " + e);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"Could not update task\"}");
        }
    }
}
