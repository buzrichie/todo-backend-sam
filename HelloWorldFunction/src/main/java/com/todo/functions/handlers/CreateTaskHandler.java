package com.todo.functions.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.model.Task;
import com.todo.utils.CorsUtils;   // ✅ using your CorsUtils
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;

public class CreateTaskHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
    private final String tableName = System.getenv("TABLE_NAME");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            // ✅ Handle preflight OPTIONS request
            if (CorsUtils.isPreflightRequest(request.getHttpMethod())) {
                return new APIGatewayProxyResponseEvent()
                        .withStatusCode(200)
                        .withHeaders(CorsUtils.createCorsHeaders())
                        .withBody("");
            }

            // Deserialize request body
            Map<String, Object> body = objectMapper.readValue(request.getBody(), Map.class);
            String description = (String) body.get("description");

            // Get UserId from Cognito claims
            String userId = request.getRequestContext().getAuthorizer().get("claims") != null
                    ? (String) ((Map<String, Object>) request.getRequestContext().getAuthorizer().get("claims")).get("sub")
                    : "anonymous";

            // Create task
            Task task = new Task(userId, description);

            // Build DynamoDB item
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("UserId", AttributeValue.builder().s(task.getUserId()).build());
            item.put("TaskId", AttributeValue.builder().s(task.getTaskId()).build());
            item.put("Description", AttributeValue.builder().s(task.getDescription()).build());
            item.put("Status", AttributeValue.builder().s(task.getStatus()).build());
            item.put("Deadline", AttributeValue.builder().n(task.getDeadline().toString()).build());
            item.put("ExpireAt", AttributeValue.builder().n(task.getExpireAt().toString()).build());

            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build());

            // ✅ Success response with CORS headers
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(201)
                    .withHeaders(CorsUtils.createCorsHeaders())
                    .withBody(objectMapper.writeValueAsString(task));

        } catch (Exception e) {
            context.getLogger().log("Error in CreateTaskHandler: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withHeaders(CorsUtils.createCorsHeaders())
                    .withBody("{\"error\":\"Could not create task\"}");
        }
    }
}
