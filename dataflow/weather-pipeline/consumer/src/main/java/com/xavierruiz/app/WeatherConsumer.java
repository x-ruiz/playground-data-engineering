package com.xavierruiz.app;

import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Validation.Required;

import org.apache.beam.sdk.extensions.gcp.options.GcpOptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.coders.CustomCoder;
import org.apache.beam.sdk.coders.SerializableCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;

import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.DoFn.ProcessElement;

import org.apache.beam.sdk.values.TypeDescriptor;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.xavierruiz.app.schemas.WeatherPipelineSchema;

public class WeatherConsumer {
  String tableSpec = "dataflow.weather_pipeline_raw";

  public interface WeatherConsumerOptions extends GcpOptions {
    // @Description("Path of file to read from")
    // @Default.String("inputs/input.txt")
    // String getInputFile();

    // void setInputFile(String value);

    @Description("Path of file to output to")
    @Default.String("outputs/output.txt")
    String getOutputFile();

    void setOutputFile(String value);

    @Description("GCP Project that google resources are located in")
    @Default.InstanceFactory(GcpOptions.DefaultProjectFactory.class)
    @Required
    String getProject();

    void setProject(String value);
  }

  // Customer Encoders
  public static class JsonObjectCoder extends CustomCoder<JsonObject> {
    private static final JsonObjectCoder INSTANCE = new JsonObjectCoder();

    public static JsonObjectCoder of() {
      return INSTANCE;
    }

    @Override
    public void encode(JsonObject value, OutputStream outStream) throws IOException {
      StringUtf8Coder.of().encode(value.toString(), outStream);
    }

    @Override
    public JsonObject decode(InputStream inStream) throws IOException {
      String jsonString = StringUtf8Coder.of().decode(inStream);
      return JsonParser.parseString(jsonString).getAsJsonObject();
    }
  }

  // Logging lines DoFn
  public static class LogLinesFn extends DoFn<String, String> {
    @ProcessElement
    public void processElement(@Element String line, OutputReceiver<String> receiver) {
      System.out.println(line);
      receiver.output(line);
    }
  }

  // Json Object Converter DoFn
  public static class JsonObjectFn extends DoFn<String, JsonObject> {
    private final Gson gson = new Gson();

    @ProcessElement
    public void processElement(@Element String line, OutputReceiver<JsonObject> receiver) {
      JsonObject jsonObject = gson.fromJson(line, JsonObject.class);
      receiver.output(jsonObject);
    }
  }

  // Convert Json Object to Table Row for Writing DoFn
  public static class TableRowFn extends DoFn<JsonObject, Void> {
    @ProcessElement
    public void processElement(@Element JsonObject jsonObject, OutputReceiver<Void> receiver) {
      System.out.printf("Test: %s", jsonObject.get("city_name").getAsString());
    }
  }

  static void runPipeline(WeatherConsumerOptions options) {
    Pipeline p = Pipeline.create(options);
    // Creates a subscription at runtime
    p.apply("PubSub Subscription",
        PubsubIO.readStrings().fromTopic("projects/unified-gist-464917-r7/topics/weather_chicago"))
        .apply("Log lines", ParDo.of(new LogLinesFn())) // ParDo: The general process to apply transformations
        .apply("Convert to Json Object", ParDo.of(new JsonObjectFn())) // Conver each line from pubsub to JsonObject
        .setCoder(JsonObjectCoder.of())
        .apply("Convert Json Object to Table Row", ParDo.of(new TableRowFn()));
    p.run().waitUntilFinish();
  }

  public static void main(String[] args) {
    System.out.println("Starting Weather Consumer Beam Pipeline Set Up");
    WeatherConsumerOptions options = PipelineOptionsFactory.fromArgs(args).withValidation()
        .as(WeatherConsumerOptions.class);

    runPipeline(options);
  }
}
