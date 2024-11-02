package com.sensor.sensor_client.model;

public class SensorReadings {
    private double temperature;
    private double pressure;
    private double humidity;
    private Double co;
    private Double no2;
    private Double so2;

    public SensorReadings(Double temperature, Double pressure, Double humidity, Double co, Double no2, Double so2) {
        this.temperature = temperature;
        this.pressure = pressure;
        this.humidity = humidity;
        this.co = co != null ? co : 0.0;
        this.no2 = no2 != null ? no2 : 0.0;
        this.so2 = so2 != null ? so2 : 0.0;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public double getPressure() {
        return pressure;
    }

    public void setPressure(double pressure) {
        this.pressure = pressure;
    }

    public double getHumidity() {
        return humidity;
    }

    public void setHumidity(double humidity) {
        this.humidity = humidity;
    }

    public Double getCo() {
        return co;
    }

    public void setCo(Double co) {
        this.co = co;
    }

    public Double getNo2() {
        return no2;
    }

    public void setNo2(Double no2) {
        this.no2 = no2;
    }

    public Double getSo2() {
        return so2;
    }

    public void setSo2(Double so2) {
        this.so2 = so2;
    }

    @Override
    public String toString() {
        return "SensorReadings{" +
                "temperature=" + temperature +
                ", pressure=" + pressure +
                ", humidity=" + humidity +
                ", co=" + (co != null ? co : "N/A") +
                ", no2=" + (no2 != null ? no2 : "N/A") +
                ", so2=" + (so2 != null ? so2 : "N/A") +
                '}';
    }

}
