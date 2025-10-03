package com.todo.functions.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.model.Task;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lambda handler for retrieving tasks of a user.
 * Triggered by GET /tasks
 */
public class GetTasksHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
    private final String tableName = System.getenv("TABLE_NAME");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            // ✅ Extract Cognito userId (sub claim)
            String userId = request.getRequestContext().getAuthorizer().get("claims") != null
                    ? (String) ((Map<String, Object>) request.getRequestContext().getAuthorizer().get("claims")).get("sub")
                    : "anonymous";

            // ✅ Query tasks for this user
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

            // ✅ Convert DynamoDB items → Task objects
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
                if (item.containsKey("ExpireAt")) {
                    task.setExpireAt(Long.parseLong(item.get("ExpireAt").n()));
                }
                tasks.add(task);
            }

            // ✅ Return clean JSON array of tasks
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(objectMapper.writeValueAsString(tasks));

        } catch (Exception e) {
            context.getLogger().log("Error in GetTasksHandler: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"Could not fetch tasks\"}");
        }
    }
}
