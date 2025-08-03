package com.xavierruiz.app;

import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.Default;

import org.apache.beam.sdk.Pipeline;

import org.apache.beam.sdk.io.TextIO;

import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.DoFn.ProcessElement;

public class WeatherConsumer {

    public interface WeatherConsumerOptions extends PipelineOptions {
        @Description("Path of file to read from")
        @Default.String("inputs/input.txt")
        String getInputFile();

        void setInputFile(String value);

        @Description("Path of file to output to")
        @Default.String("outputs/output.txt")
        String getOutputFile();

        void setOutputFile(String value);
    }

    // Logging lines
    public static class LogLinesFn extends DoFn<String, Void> {
        @ProcessElement
        public void processElement(@Element String line, OutputReceiver<Void> receiver) {
            System.out.println(line);
        }
    }

    static void runPipeline(WeatherConsumerOptions options) {
        Pipeline p = Pipeline.create(options);
        p.apply("Read Lines", TextIO.read().from(options.getInputFile()))
                .apply("Log lines", ParDo.of(new LogLinesFn()));
        p.run().waitUntilFinish();
    }

    public static void main(String[] args) {
        System.out.println("Starting Weather Consumer Beam Pipeline Set Up");
        WeatherConsumerOptions options = PipelineOptionsFactory.fromArgs(args).withValidation()
                .as(WeatherConsumerOptions.class);

        runPipeline(options);
    }
}
