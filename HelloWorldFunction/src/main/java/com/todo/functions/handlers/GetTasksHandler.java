package com.todo.functions.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.model.Task;
import com.todo.utils.CorsUtils;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetTasksHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
    private final String tableName = System.getenv("TABLE_NAME");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        // Handle CORS preflight request
        if (CorsUtils.isPreflightRequest(request.getHttpMethod())) {
            return createCorsResponse();
        }

        Map<String, String> headers = CorsUtils.createCorsHeaders();

        try {
            String userId = extractUserIdFromRequest(request);

            // Query DynamoDB for tasks belonging to this user
            Map<String, String> expressionAttributesNames = new HashMap<>();
            expressionAttributesNames.put("#uid", "UserId");

            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
            expressionAttributeValues.put(":uid", AttributeValue.builder().s(userId).build());

            QueryRequest queryRequest = QueryRequest.builder()
                    .tableName(tableName)
                    .keyConditionExpression("#uid = :uid")
                    .expressionAttributeNames(expressionAttributesNames)
                    .expressionAttributeValues(expressionAttributeValues)
                    .build();

            List<Map<String, AttributeValue>> items = dynamoDbClient.query(queryRequest).items();

            // Convert DynamoDB items to Task objects
            List<Task> tasks = new ArrayList<>();
            for (Map<String, AttributeValue> item : items) {
                Task task = new Task();
                task.setTaskId(item.get("TaskId").s());
                task.setUserId(item.get("UserId").s());
                task.setDescription(item.getOrDefault("Description", AttributeValue.builder().s("").build()).s());
                task.setStatus(item.getOrDefault("Status", AttributeValue.builder().s("Pending").build()).s());
                if (item.containsKey("Deadline")) {
                    task.setDeadline(Long.parseLong(item.get("Deadline").n()));
                }
                if (item.containsKey("expireAt")) {
                    task.setExpireAt(Long.parseLong(item.get("expireAt").n()));
                }
                tasks.add(task);
            }

            // Return tasks as JSON
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(headers)
                    .withBody(objectMapper.writeValueAsString(tasks));

        } catch (Exception e) {
            context.getLogger().log("Error in GetTasksHandler: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(headers)
                    .withBody("{\"error\":\"Could not fetch tasks\"}");
        }
    }

    private String extractUserIdFromRequest(APIGatewayProxyRequestEvent request) {
        try {
            if (request.getRequestContext().getAuthorizer() != null &&
                    request.getRequestContext().getAuthorizer().get("claims") != null) {
                Map<String, Object> claims = (Map<String, Object>) request.getRequestContext().getAuthorizer().get("claims");
                return (String) claims.get("sub");
            }
            return "anonymous";
        } catch (Exception e) {
            return "anonymous";
        }
    }

    private APIGatewayProxyResponseEvent createCorsResponse() {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withHeaders(CorsUtils.createCorsHeaders())
                .withBody("");
    }
}