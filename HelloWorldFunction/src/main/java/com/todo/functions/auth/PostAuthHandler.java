package com.todo.functions.auth;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.CognitoUserPoolPostAuthenticationEvent;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.Map;

public class PostAuthHandler implements RequestHandler<CognitoUserPoolPostAuthenticationEvent, CognitoUserPoolPostAuthenticationEvent> {

    private final SnsClient snsClient = SnsClient.create();

    @Override
    public CognitoUserPoolPostAuthenticationEvent handleRequest(CognitoUserPoolPostAuthenticationEvent event, Context context) {
        String topicArn = System.getenv("TOPIC_ARN");

        // ✅ userAttributes are under request
        Map<String, String> userAttributes = event.getRequest().getUserAttributes();

        String username = event.getUserName();
        String email = userAttributes.get("email");

        String message = String.format("User %s has successfully signed in. Email: %s", username, email);

        try {
            PublishRequest request = PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(message)
                    .build();

            PublishResponse result = snsClient.publish(request);
            context.getLogger().log("SNS Notification Sent: " + result.messageId());
        } catch (Exception e) {
            context.getLogger().log("Failed to publish SNS message: " + e.getMessage());
        }
        return event;
    }
}
