package com.sensor.sensor_client.api;

import com.sensor.sensor_client.model.Sensor;
import com.sensor.sensor_client.model.SensorDTO;
import com.sensor.sensor_client.model.SensorReadings;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface SensorApiService {

    @POST("/api/sensors/register")
    Call<Void> registerSensor(@Body SensorDTO sensorDTO);

    @GET("/api/sensors/{id}/closest-neighbor")
    Call<SensorDTO> getClosestNeighbor(@Path("id") Long sensorId);

    @GET("/api/sensors/{id}")
    Call<Sensor> getSensorById(@Path("id") Long id);

    @POST("/api/sensors/{id}/readings")
    Call<Void> saveReading(@Path("id") Long sensorId, @Body SensorReadings reading);

}
