package com.xavierruiz.app;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Main {
    public static void main(String[] args) {
        String projectId = "unified-gist-464917-r7";
        String subscriptionId = "test-subscription";
        System.out.printf("Consuming messages from subscription %s \n", subscriptionId);
        subscribeAsync(projectId, subscriptionId);
    }

    private static void subscribeAsync(String projectId, String subscriptionId) {
        ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId, subscriptionId);

        // Define a lambda function to handle incoming messages.
        // A lambda function is a concise way to implement a functional interface (an
        // interface with a single abstract method).
        // Here, the lambda implements the MessageReceiver interface, which requires
        // handling a PubsubMessage and an AckReplyConsumer.
        MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
            // handle the message and then ack
            System.out.println("Id: " + message.getMessageId());
            System.out.println("Data: " + message.getData().toStringUtf8());
            consumer.ack();
        };

        Subscriber subscriber = null;
        try {
            // Use the builder pattern to create a Subscriber instance.
            // The builder pattern is a design pattern used to construct complex objects
            // step-by-step.
            // It ensures the Subscriber is configured correctly with the subscription name
            // and message receiver.
            subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();
            subscriber.startAsync().awaitRunning();
            System.out.printf("Listening for messages on %s: \n", subscriptionName.toString());
            subscriber.awaitTerminated(30, TimeUnit.SECONDS);
        } catch (TimeoutException timeoutException) {
            subscriber.stopAsync();
        }
    }
}
