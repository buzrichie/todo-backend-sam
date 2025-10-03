package com.todo.functions.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * Lambda handler for deleting a Task.
 * Triggered by DELETE /tasks/{taskId}
 */
public class DeleteTaskHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
    private final String tableName = System.getenv("TABLE_NAME");

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String taskId = request.getPathParameters().get("taskId");

            // Get userId from Cognito claims
            String userId = request.getRequestContext().getAuthorizer().get("claims") != null
                    ? (String) ((Map<String, Object>) request.getRequestContext().getAuthorizer().get("claims")).get("sub")
                    : "anonymous";

            // Build key
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("UserId", AttributeValue.builder().s(userId).build());
            key.put("TaskId", AttributeValue.builder().s(taskId).build());

            // Delete item
            dynamoDbClient.deleteItem(DeleteItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .build());

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody("{\"message\":\"Task deleted successfully\"}");

        } catch (Exception e) {
            context.getLogger().log("Error in DeleteTaskHandler: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"Could not delete task\"}");
        }
    }
}
