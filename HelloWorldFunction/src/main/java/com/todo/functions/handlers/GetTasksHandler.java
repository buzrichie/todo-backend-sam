package com.todo.functions.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

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
            // Get UserId from Cognito claims
            String userId = request.getRequestContext().getAuthorizer().get("claims") != null
                    ? (String) ((Map<String, Object>) request.getRequestContext().getAuthorizer().get("claims")).get("sub")
                    : "anonymous";

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

            // Return tasks as JSON
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody(objectMapper.writeValueAsString(items));

        } catch (Exception e) {
            context.getLogger().log("Error in GetTasksHandler: " + e.getMessage());
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"Could not fetch tasks\"}");
        }
    }
}
