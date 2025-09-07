package com.xavierruiz.app;

import com.xavierruiz.models.Data;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.gson.Gson;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// TODO: Add iceberg table sink and convert this to a flink job
public class Main {
    public static void main(String[] args) {
        String projectId = "unified-gist-464917-r7";
        String subscriptionId = "weather_chicago";
        System.out.printf("Consuming messages from subscription %s \n", subscriptionId);
        subscribeAsync(projectId, subscriptionId);
    }

    private static void subscribeAsync(String projectId, String subscriptionId) {
        ProjectSubscriptionName subscriptionName = ProjectSubscriptionName.of(projectId, subscriptionId);
        Gson gson = new Gson();
        // Define a lambda function to handle incoming messages.
        // A lambda function is a concise way to implement a functional interface (an
        // interface with a single abstract method).
        // Here, the lambda implements the MessageReceiver interface, which requires
        // handling a PubsubMessage and an AckReplyConsumer.
        MessageReceiver receiver = (PubsubMessage message, AckReplyConsumer consumer) -> {
            // handle the message and then ack
            System.out.printf("Deserializing JSON from message id: %s \n", message.getMessageId());
            consumer.ack(); // ack first then process
            Data data = gson.fromJson(message.getData().toStringUtf8(), Data.class);
            String localTime = data.getLocation().getLocaltime();
            System.out.println(localTime);
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

            // Blocking main thread until forcefully terminated
            subscriber.awaitTerminated();
        } catch (IllegalStateException e) {
            System.err.println("Error with subscriber state: " + e.getMessage());
        } finally {
            // This block is crucial for ensuring a clean shutdown.
            // It will be executed when the program is terminated.
            if (subscriber != null && subscriber.isRunning()) {
                System.out.println("\nStopping subscriber...");
                try {
                    // Initiate the shutdown and wait a reasonable time for it to complete.
                    subscriber.stopAsync().awaitTerminated(10, TimeUnit.SECONDS);
                    System.out.println("Subscriber stopped successfully.");
                } catch (TimeoutException e) {
                    System.err.println("Subscriber did not stop within the given timeout. Forcefully terminating.");
                    // Forcefully terminate if it doesn't shut down gracefully.
                    subscriber.stopAsync().awaitTerminated();
                }
            }
        }
    }
}