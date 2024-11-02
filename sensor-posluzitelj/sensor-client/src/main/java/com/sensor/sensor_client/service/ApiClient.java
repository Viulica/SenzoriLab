package com.sensor.sensor_client.service;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sensor.sensor_client.api.SensorApiService;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.time.LocalDateTime;


public class ApiClient {
    private static Retrofit retrofit = null;

    public static SensorApiService getSensorApiService() {
        if (retrofit == null) {

            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                    .serializeNulls()
                    .create();
            retrofit = new Retrofit.Builder()
                    .baseUrl("http://localhost:8080/")
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .build();
        }
        return retrofit.create(SensorApiService.class);
    }
}
