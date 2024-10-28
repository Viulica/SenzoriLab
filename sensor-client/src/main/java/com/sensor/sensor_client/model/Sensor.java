package com.sensor.sensor_client.model;
import com.sensor.sensor_client.api.SensorApiService;
import com.sensor.sensor_client.service.ApiClient;
import com.sensor.grpc.SensorServiceGrpc;
import com.sensor.grpc.SensorIdRequest;
import com.sensor.grpc.SensorReadingsRequest;
import com.sensor.grpc.SensorReadingsResponse;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static java.lang.Math.abs;


public class Sensor {
    private final LocalDateTime startTime;
    private double latitude;
    private double longitude;
    private static final String CSV_FILE_PATH = "src/main/resources/readings.csv";
    private Long id;
    private String ip;
    private int port;
    private transient SensorApiService sensorApiService;

    private transient List<SensorReadings> ocitanja;
    private Server grpcServer;


    public Sensor(String ip, int port) {
        this.startTime = LocalDateTime.now();
        this.ip = ip;
        this.port = port;
        this.id = 0L;
        this.ocitanja = new ArrayList<>();
        lokacija();
    }

    public void registerSensorAsync() {
        new Thread(() -> {
            register();
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            try {
                startGeneratingReadings();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }).start();
    }


    public void register() {

        SensorApiService service = getSensorApiService();
        Call<Void> call = service.registerSensor(new SensorDTO(this.latitude, this.longitude, this.ip, this.port));

        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    if (response.code() == 201) {
                        String locationHeader = response.headers().get("Location");
                        String id = response.headers().get("Generated-Id");
                        System.out.println("Sensor registered successfully. Location: " + locationHeader);

                        if (id != null) {
                            setId(Long.parseLong(id));
                        }

                        try {
                            startGrpcServer();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                    } else {
                        System.out.println("Unexpected status code: " + response.code());
                    }
                } else {
                    System.out.println("Failed to register sensor. HTTP Status: " + response.code());
                    System.exit(1);
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                System.out.println("Error during sensor registration: " + t.getMessage());
            }
        });
    }

    public void startGeneratingReadings() throws IOException, InterruptedException {
        while (true) {
            SensorReadings reading = generirajOcitavanje();
            this.ocitanja.add(reading);
            System.out.println("Generirano očitanje za senzor " + this.getPort() + ": " + reading);

            SensorDTO closestNeighbor = requestClosestNeighbor();

            if (closestNeighbor == null) {
                System.out.println("Nema dostupnog susjeda za kalibraciju za senzor " + this.getPort());
                saveReading(reading);
            } else {
                System.out.println("Šaljem grpc request senzoru " + closestNeighbor.getPort());
                sendGrpcRequestToNeighbor(closestNeighbor);
            }

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public SensorReadings generirajOcitavanje() {


        long brojAktivnihSekundi = abs(ChronoUnit.SECONDS.between(startTime, LocalDateTime.now()));
        brojAktivnihSekundi++;
        int row = (int) ((brojAktivnihSekundi % 100) + 1);

        String[] readings = getReading(row);

        if (readings == null) {
            throw new IllegalArgumentException("Ne mogu pronaći očitanje za redak " + row);
        }

        double temperature = Double.parseDouble(readings[0]);
        double pressure = Double.parseDouble(readings[1]);
        double humidity = Double.parseDouble(readings[2]);

        Double co = (readings[3] != null && !readings[3].isEmpty()) ? Double.parseDouble(readings[3]) : null;
        Double no2 = (readings[4] != null && !readings[4].isEmpty()) ? Double.parseDouble(readings[4]) : null;
        Double so2 = (readings.length > 5 && readings[5] != null && !readings[5].isEmpty()) ? Double.parseDouble(readings[5]) : null;


        SensorReadings newReading = new SensorReadings(temperature, pressure, humidity, co, no2, so2);
        ocitanja.add(newReading);

        return newReading;
    }

    public static String[] getReading(int rowIndex) {
        try (BufferedReader br = new BufferedReader(new FileReader(CSV_FILE_PATH))) {
            String line;
            int currentRow = 0;

            while ((line = br.readLine()) != null) {
                if (currentRow == rowIndex) {
                    return line.split(",");
                }
                currentRow++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public SensorDTO requestClosestNeighbor() {
        SensorApiService sensorApiService = ApiClient.getSensorApiService();

        Call<SensorDTO> call = sensorApiService.getClosestNeighbor(this.id);

        try {
            Response<SensorDTO> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {

                SensorDTO closestNeighbor = response.body();
                return closestNeighbor;
            } else {
                System.out.println("Failed to get closest neighbor: " + response.code());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void startGrpcServer() throws IOException {

        grpcServer = ServerBuilder.forPort(this.getPort())
                .addService(new SensorServiceImpl(this))
                .build()
                .start();

        System.out.println("grpc server started for sensor " + this.getId());


        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down gRPC server for sensor: " + this.getId());
            stopGrpcServer();
        }));

    }

    public void stopGrpcServer() {
        if (grpcServer != null) {
            grpcServer.shutdown();
        }
    }

    private void sendGrpcRequestToNeighbor(SensorDTO sensorDTO) throws IOException, InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(sensorDTO.getIp(), sensorDTO.getPort())
                .usePlaintext()
                .build();

        SensorServiceGrpc.SensorServiceBlockingStub stub = SensorServiceGrpc.newBlockingStub(channel);

        SensorIdRequest request = SensorIdRequest.newBuilder()
                .setId(this.id)
                .build();

        try {
            SensorReadingsResponse response = stub.requestReadings(request);

            SensorReadings kalibrirano;
            SensorReadings lastOwnReading = this.getOcitanja().get(this.getOcitanja().size() - 1);
            if (response.getSuccess()) {
                kalibrirano = kalibriraj(this.getOcitanja().get(this.getOcitanja().size() - 1)
                        ,
                        new SensorReadings(
                                response.getTemperature(),
                                response.getPressure(),
                                response.getHumidity(),
                                response.getCo(),
                                response.getNo2(),
                                response.getSo2()
                        ));
            } else {
                kalibrirano = lastOwnReading;
            }
            System.out.println("kalibrirano očitanje: " + kalibrirano);
            saveReading(kalibrirano);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            channel.shutdown();
        }
    }

    public SensorReadings kalibriraj(SensorReadings ownReading, SensorReadings neighborReading) {
        System.out.println("ownReading: " + ownReading + ", neighborReading: " + neighborReading);
        Double temperature = average(ownReading.getTemperature(), neighborReading.getTemperature());
        Double pressure = average(ownReading.getPressure(), neighborReading.getPressure());
        Double humidity = average(ownReading.getHumidity(), neighborReading.getHumidity());
        Double co = average(ownReading.getCo(), neighborReading.getCo());
        Double no2 = average(ownReading.getNo2(), neighborReading.getNo2());
        Double so2 = average(ownReading.getSo2(), neighborReading.getSo2());

        return new SensorReadings(temperature, pressure, humidity, co, no2, so2);
    }

    private Double average(Double value1, Double value2) {
        if (value1 == null && value2 == null) return null;
        if (value1 == null || value1 == 0) return value2;
        if (value2 == null || value2 == 0) return value1;
        return (value1 + value2) / 2;
    }

    public void saveReading(SensorReadings reading) {

        SensorApiService service = getSensorApiService();
        Call<Void> call = service.saveReading(this.id, reading);

        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful() && response.code() == 201) {
                    String location = response.headers().get("Location");
                    System.out.println("Reading saved successfully at: " + location);
                } else if (response.code() == 204) {
                    System.out.println("No sensor found for reading storage.");
                } else {
                    System.out.println("Failed to save reading, status code: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                System.out.println("Error while saving reading: " + t.getMessage());
            }
        });
    }


    private void lokacija() {
        Random random = new Random();
        this.latitude = 45.75 + (random.nextDouble() * (45.85 - 45.75));
        this.longitude = 15.87 + (random.nextDouble() * (16 - 15.87));
    }

    private SensorApiService getSensorApiService() {
        if (this.sensorApiService == null) {
            this.sensorApiService = ApiClient.getSensorApiService();
        }
        return this.sensorApiService;
    }


    public double getLatitude() {
        return latitude;
    }


    public double getLongitude() {
        return longitude;
    }


    public String getIp() {
        return ip;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }


    public int getPort() {
        return port;
    }

    public List<SensorReadings> getOcitanja() {
        return ocitanja;
    }

    @Override
    public String toString() {
        return "Sensor{id=" + id + "}";
    }

    static class SensorServiceImpl extends SensorServiceGrpc.SensorServiceImplBase {

        private final Sensor sensor;

        public SensorServiceImpl(Sensor sensor) {
            this.sensor = sensor;
        }


        @Override
        public void requestReadings(SensorIdRequest request,
                                    StreamObserver<SensorReadingsResponse> responseObserver) {
            System.out.println("Received gRPC request for readings from sensor ID: " + request.getId());

            SensorReadings lastReading = sensor.ocitanja.isEmpty() ?
                    null : sensor.ocitanja.get(sensor.ocitanja.size() - 1);

            SensorReadingsResponse.Builder responseBuilder = SensorReadingsResponse.newBuilder()
                    .setId(sensor.getId())
                    .setSuccess(lastReading != null);

            if (lastReading != null) {
                responseBuilder
                        .setTemperature(lastReading.getTemperature())
                        .setPressure(lastReading.getPressure())
                        .setHumidity(lastReading.getHumidity())
                        .setCo(lastReading.getCo() != null ? lastReading.getCo() : 0.0)
                        .setNo2(lastReading.getNo2() != null ? lastReading.getNo2() : 0.0)
                        .setSo2(lastReading.getSo2() != null ? lastReading.getSo2() : 0.0);
            }

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();
        }

        @Override
        public void sendReadings(SensorReadingsRequest request, StreamObserver<SensorReadingsResponse> responseObserver) {
            System.out.println("Received readings from sensor ID: " + request.getId());

            String message = "Readings received: " + request.getTemperature() + "°C, " + request.getPressure() + " Pa";
            System.out.println(message);

            SensorReadingsResponse response = SensorReadingsResponse.newBuilder()
                    .setId(sensor.getId())
                    .setSuccess(true)
                    .setTemperature(request.getTemperature())
                    .setPressure(request.getPressure())
                    .setHumidity(request.getHumidity())
                    .setCo(request.getCo())
                    .setNo2(request.getNo2())
                    .setSo2(request.getSo2())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

}




