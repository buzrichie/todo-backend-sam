package com.todo.functions.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.utils.CorsUtils;   // ✅ include CORS helper
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
            // ✅ Handle CORS preflight
            if (CorsUtils.isPreflightRequest(request.getHttpMethod())) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(CorsUtils.createCorsHeaders())
                        .withBody("");
            }

            String taskId = request.getPathParameters().get("taskId");

            // ✅ Get userId from Cognito claims safely
            String userId = request.getRequestContext().getAuthorizer().get("claims") != null
                    ? (String) ((Map<String, Object>) request.getRequestContext().getAuthorizer().get("claims")).get("sub")
                    : "anonymous";

            // ✅ Parse request body
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

            // ✅ Return 400 if no fields to update
            if (expressionValues.isEmpty()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(400)
                        .withHeaders(CorsUtils.createCorsHeaders())
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
                    .returnValues("UPDATED_NEW") // optional, for debugging
                    .build());

            // ✅ Success response with CORS
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(CorsUtils.createCorsHeaders())
                    .withBody("{\"message\":\"Task updated successfully\"}");

        } catch (Exception e) {
            context.getLogger().log("Error in UpdateTaskHandler: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(CorsUtils.createCorsHeaders())
                    .withBody("{\"error\":\"Could not update task\"}");
        }
    }
}
