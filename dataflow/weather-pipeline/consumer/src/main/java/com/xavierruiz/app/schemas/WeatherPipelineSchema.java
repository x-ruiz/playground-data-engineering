package com.xavierruiz.app.schemas;

import java.util.List;
import java.util.ArrayList;

import com.google.api.services.bigquery.model.TableSchema;
import com.google.api.services.bigquery.model.TableFieldSchema;

public class WeatherPipelineSchema {

    public static TableSchema getFields() {
        List<TableFieldSchema> fields = new ArrayList<>();

        fields.add(new TableFieldSchema().setName("city_name").setType("STRING"));
        fields.add(new TableFieldSchema().setName("region").setType("STRING"));
        fields.add(new TableFieldSchema().setName("country").setType("STRING"));
        fields.add(new TableFieldSchema().setName("lat").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("lon").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("tz_id").setType("STRING"));
        fields.add(new TableFieldSchema().setName("localtime_epoch").setType("INTEGER"));
        fields.add(new TableFieldSchema().setName("localtime").setType("TIMESTAMP"));

        // Define the nested 'condition' schema
        List<TableFieldSchema> conditionFields = new ArrayList<>();
        conditionFields.add(new TableFieldSchema().setName("text").setType("STRING"));
        conditionFields.add(new TableFieldSchema().setName("icon").setType("STRING"));
        conditionFields.add(new TableFieldSchema().setName("code").setType("INTEGER"));
        TableFieldSchema conditionSchema = new TableFieldSchema().setName("condition").setType("RECORD")
                .setFields(conditionFields);

        fields.add(new TableFieldSchema().setName("last_updated_epoch").setType("INTEGER"));
        fields.add(new TableFieldSchema().setName("last_updated").setType("TIMESTAMP"));
        fields.add(new TableFieldSchema().setName("temp_c").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("temp_f").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("is_day").setType("INTEGER"));
        fields.add(conditionSchema); // Add the nested condition schema
        fields.add(new TableFieldSchema().setName("wind_mph").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("wind_kph").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("wind_degree").setType("INTEGER"));
        fields.add(new TableFieldSchema().setName("wind_dir").setType("STRING"));
        fields.add(new TableFieldSchema().setName("pressure_mb").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("pressure_in").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("precip_mm").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("precip_in").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("humidity").setType("INTEGER"));
        fields.add(new TableFieldSchema().setName("cloud").setType("INTEGER"));
        fields.add(new TableFieldSchema().setName("feelslike_c").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("feelslike_f").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("windchill_c").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("windchill_f").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("heatindex_c").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("heatindex_f").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("dewpoint_c").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("dewpoint_f").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("vis_km").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("vis_miles").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("uv").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("gust_mph").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("gust_kph").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("short_rad").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("diff_rad").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("dni").setType("FLOAT"));
        fields.add(new TableFieldSchema().setName("gti").setType("FLOAT"));

        return new TableSchema().setFields(fields);
    }
}
