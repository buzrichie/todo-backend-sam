package com.todo.functions.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.model.Task;
import com.todo.utils.CorsUtils; // ✅ Import CORS helper
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Lambda handler for retrieving a single task by ID.
 * Triggered by GET /tasks/{taskId}
 */
public class GetTaskByIdHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
    private final String tableName = System.getenv("TABLE_NAME");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            // ✅ Handle preflight OPTIONS
            if (CorsUtils.isPreflightRequest(request.getHttpMethod())) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(CorsUtils.createCorsHeaders())
                        .withBody("");
            }

            // ✅ Extract userId from JWT claims
            String userId = request.getRequestContext().getAuthorizer().get("claims") != null
                    ? (String) ((Map<String, Object>) request.getRequestContext().getAuthorizer().get("claims")).get("sub")
                    : "anonymous";

            // ✅ Get taskId from path parameter
            String taskId = request.getPathParameters().get("taskId");

            // ✅ Build key (UserId + TaskId)
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("UserId", AttributeValue.builder().s(userId).build());
            key.put("TaskId", AttributeValue.builder().s(taskId).build());

            // ✅ Fetch item from DynamoDB
            GetItemRequest getItemRequest = GetItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .build();

            Map<String, AttributeValue> item = dynamoDbClient.getItem(getItemRequest).item();

            if (item == null || item.isEmpty()) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(404)
                        .withHeaders(CorsUtils.createCorsHeaders()) // ✅ Add headers
                        .withBody("{\"error\":\"Task not found\"}");
            }

            // ✅ Convert to Task
            Task task = new Task();
            task.setTaskId(item.get("TaskId").s());
            task.setUserId(item.get("UserId").s());
            task.setDescription(item.getOrDefault("Description", AttributeValue.builder().s("").build()).s());
            task.setStatus(item.getOrDefault("Status", AttributeValue.builder().s("Pending").build()).s());
            if (item.containsKey("Deadline")) {
                task.setDeadline(Long.parseLong(item.get("Deadline").n()));
            }
            if (item.containsKey("ExpireAt")) {
                task.setExpireAt(Long.parseLong(item.get("ExpireAt").n()));
            }

            // ✅ Return JSON with CORS headers
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(CorsUtils.createCorsHeaders())
                    .withBody(objectMapper.writeValueAsString(task));

        } catch (Exception e) {
            context.getLogger().log("Error in GetTaskByIdHandler: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(CorsUtils.createCorsHeaders()) // ✅ Ensure headers on error
                    .withBody("{\"error\":\"Could not fetch task\"}");
        }
    }
}
