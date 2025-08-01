package com.xavierruiz.app;

import com.xavierruiz.app.utils.WeatherApi;
import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

// Flink Application that makes a request to endpoint and produces data to a pubsub sink
public class Main {
    public static void main(String[] args) {
        long pollingInterval = 30000; // 30 seconds
        int iterations = 10;
        WeatherApi chicagoApi = new WeatherApi("chicago");

        for (int i = 0; i <= iterations; i++) {
            System.out.printf("Iteration %d \n", i);
            String response = chicagoApi.getRequest();

            // TODO: Add filter to only publish night time weather

            // Produce to a topic
            String topic = "weather_chicago";
            String project = "unified-gist-464917-r7";
            Publisher publisher = createPublisher(project, topic);
            publishMessage(response, publisher);
            try {
                System.out.printf("Sleeping for %d seconds \n", pollingInterval / 1000);
                Thread.sleep(pollingInterval);
            } catch (InterruptedException e) {
                System.out.println("Sleep execution interrupted: " + e);
            }
            shutdownPublisher(publisher);
        }
    }

    private static Publisher createPublisher(String projectId, String topicId) {
        System.out.printf("Creating publisher for topic %s \n", topicId);
        TopicName topicName = TopicName.of(projectId, topicId);
        Publisher publisher = null;
        try {
            publisher = Publisher.newBuilder(topicName).build();
        } catch (IOException e) {
            System.out.println("Failed to create publisher " + e);
        }
        return publisher;
    }

    private static void publishMessage(String message, Publisher publisher) {
        System.out.printf("Publishing message %s for topic %s \n", message, publisher.getTopicName());

        ByteString data = ByteString.copyFromUtf8(message); // convert string to bytestring
        PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(data).build();

        ApiFuture<String> messageIdFuture = publisher.publish(pubsubMessage);

        try {
            String messageId = messageIdFuture.get();
            System.out.println("Published Message ID: " + messageId);
        } catch (InterruptedException | ExecutionException e) {
            System.out.printf("Error publishing message %s to topic %s \n", message, publisher.getTopicName());
        }

    }

    private static void shutdownPublisher(Publisher publisher) {
        System.out.println("Shutting down publisher for topic " + publisher.getTopicName());
        publisher.shutdown();
        try {
            publisher.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            System.out.println("Error shutting down publisher " + e);
        }
    }
}
