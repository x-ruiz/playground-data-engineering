package com.xavierruiz.app;

import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Validation.Required;
import org.apache.beam.runners.dataflow.DataflowRunner;
import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;

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

import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.TableSchema;

import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;

import com.xavierruiz.app.schemas.WeatherPipelineSchema;

public class WeatherConsumer {

  // public interface WeatherConsumerOptions extends GcpOptions {
  // // @Description("Path of file to read from")
  // // @Default.String("inputs/input.txt")
  // // String getInputFile();

  // // void setInputFile(String value);

  // @Description("Path of file to output to")
  // @Default.String("outputs/output.txt")
  // String getOutputFile();

  // void setOutputFile(String value);

  // @Description("GCP Project that google resources are located in")
  // @Default.InstanceFactory(GcpOptions.DefaultProjectFactory.class)
  // @Required
  // String getProject();

  // void setProject(String value);
  // }

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
    private transient Gson gson;

    @Setup
    public void setup() {
      gson = new Gson(); // Initialize Gson during setup
    }

    @ProcessElement
    public void processElement(@Element String line, OutputReceiver<JsonObject> receiver) {
      JsonObject jsonObject = gson.fromJson(line, JsonObject.class);
      receiver.output(jsonObject); // Emit the JsonObject
    }
  }

  // Convert Json Object to Table Row for Writing DoFn
  public static class JsonToTableRowFn extends DoFn<JsonObject, TableRow> {
    @ProcessElement
    public void processElement(@Element JsonObject jsonObject, OutputReceiver<TableRow> receiver) {
      JsonObject location = jsonObject.get("location").getAsJsonObject();
      JsonObject current = jsonObject.get("current").getAsJsonObject();
      System.out.printf("Writing Received Message to BigQuery table - Event time: %s \n",
          location.get("localtime").getAsString());

      TableRow row = new TableRow();
      // Populate TableRow fields based on schema
      row.set("city_name", location.get("name").getAsString());
      row.set("region", location.get("region").getAsString());
      row.set("country", location.get("country").getAsString());
      row.set("lat", location.get("lat").getAsDouble());
      row.set("lon", location.get("lon").getAsDouble());
      row.set("tz_id", location.get("tz_id").getAsString());
      row.set("localtime_epoch", location.get("localtime_epoch").getAsInt());
      row.set("localtime", location.get("localtime").getAsString());

      // Current weather fields
      row.set("last_updated_epoch", current.get("last_updated_epoch").getAsInt());
      row.set("last_updated", current.get("last_updated").getAsString());
      row.set("temp_c", current.get("temp_c").getAsDouble());
      row.set("temp_f", current.get("temp_f").getAsDouble());
      row.set("is_day", current.get("is_day").getAsInt());
      row.set("wind_mph", current.get("wind_mph").getAsDouble());
      row.set("wind_kph", current.get("wind_kph").getAsDouble());
      row.set("wind_degree", current.get("wind_degree").getAsInt());
      row.set("wind_dir", current.get("wind_dir").getAsString());
      row.set("pressure_mb", current.get("pressure_mb").getAsDouble());
      row.set("pressure_in", current.get("pressure_in").getAsDouble());
      row.set("precip_mm", current.get("precip_mm").getAsDouble());
      row.set("precip_in", current.get("precip_in").getAsDouble());
      row.set("humidity", current.get("humidity").getAsInt());
      row.set("cloud", current.get("cloud").getAsInt());
      row.set("feelslike_c", current.get("feelslike_c").getAsDouble());
      row.set("feelslike_f", current.get("feelslike_f").getAsDouble());
      row.set("windchill_c", current.get("windchill_c").getAsDouble());
      row.set("windchill_f", current.get("windchill_f").getAsDouble());
      row.set("heatindex_c", current.get("heatindex_c").getAsDouble());
      row.set("heatindex_f", current.get("heatindex_f").getAsDouble());
      row.set("dewpoint_c", current.get("dewpoint_c").getAsDouble());
      row.set("dewpoint_f", current.get("dewpoint_f").getAsDouble());
      row.set("vis_km", current.get("vis_km").getAsDouble());
      row.set("vis_miles", current.get("vis_miles").getAsDouble());
      row.set("uv", current.get("uv").getAsDouble());
      row.set("gust_mph", current.get("gust_mph").getAsDouble());
      row.set("gust_kph", current.get("gust_kph").getAsDouble());
      row.set("short_rad", current.get("short_rad").getAsDouble());
      row.set("diff_rad", current.get("diff_rad").getAsDouble());
      row.set("dni", current.get("dni").getAsDouble());
      row.set("gti", current.get("gti").getAsDouble());

      // Nested 'condition' record
      JsonObject condition = current.get("condition").getAsJsonObject();
      TableRow conditionRow = new TableRow();
      conditionRow.set("text", condition.get("text").getAsString());
      conditionRow.set("icon", condition.get("icon").getAsString());
      conditionRow.set("code", condition.get("code").getAsInt());
      row.set("condition", conditionRow);

      // Output the TableRow
      receiver.output(row);
    }
  }

  static void runPipeline(DataflowPipelineOptions options) {
    // static void runPipeline(WeatherConsumerOptions options) {
    String tableSpec = "dataflow.weather_pipeline_raw";
    TableSchema weatherSchema = WeatherPipelineSchema.getFields();

    Pipeline p = Pipeline.create(options);
    // Creates a subscription at runtime
    p.apply("PubSub Subscription",
        PubsubIO.readStrings().fromSubscription("projects/unified-gist-464917-r7/subscriptions/weather_chicago"))
        // .apply("Log lines", ParDo.of(new LogLinesFn())) // ParDo: The general process
        // to apply transformations
        .apply("Convert to Json Object", ParDo.of(new JsonObjectFn())) // Conver each line from pubsub to JsonObject
        .setCoder(JsonObjectCoder.of())
        .apply("Convert Json Object to Table Row", ParDo.of(new JsonToTableRowFn()))
        .apply("Write to BigQuery Iceberg Raw Table",
            BigQueryIO.writeTableRows().to(tableSpec).withSchema(weatherSchema)
                .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_NEVER) // Throws error if table does
                                                                                        // not exist
                .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND)); // Appends new rows to existing
                                                                                        // rows
    p.run().waitUntilFinish();
  }

  public static void main(String[] args) {
    System.out.println("Starting Weather Consumer Beam Pipeline Set Up");
    // WeatherConsumerOptions options =
    // PipelineOptionsFactory.fromArgs(args).withValidation()
    // .as(WeatherConsumerOptions.class);
    DataflowPipelineOptions options = PipelineOptionsFactory.fromArgs(args).withValidation()
        .as(DataflowPipelineOptions.class);
    options.setRunner(DataflowRunner.class);

    // Set other Dataflow-specific options
    options.setProject("unified-gist-464917-r7");
    options.setRegion("us-central1");
    options.setGcpTempLocation("gs://raw_data_0spq/dataflow/weather-pipeline/temp");
    // options.setGcpStagingLocation("gs://raw_data_0spq/dataflow/weather-pipeline/staging");

    runPipeline(options);
  }
}
