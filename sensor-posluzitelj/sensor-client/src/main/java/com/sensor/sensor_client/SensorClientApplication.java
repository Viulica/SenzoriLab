package com.sensor.sensor_client;

import com.sensor.sensor_client.model.Sensor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;

@SpringBootApplication
public class SensorClientApplication implements CommandLineRunner {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(SensorClientApplication.class);
		app.setWebApplicationType(WebApplicationType.NONE);
		app.run(args);
	}

	@Override
	public void run(String... args) throws Exception {
		Sensor sensor1 = new Sensor("127.0.0.1", 8081);
		Sensor sensor2 = new Sensor("127.0.0.1", 8082);
		Sensor sensor3 = new Sensor("127.0.0.1", 8083);

		sensor1.registerSensorAsync();
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		sensor2.registerSensorAsync();
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		sensor3.registerSensorAsync();
	}

}

