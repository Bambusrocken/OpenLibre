package de.dorianscholz.openlibre.model;

import java.util.concurrent.TimeUnit;

import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class ReadingData extends RealmObject {
    public static final String ID = "id";
    static final String SENSOR = "sensor";
    static final String SENSOR_AGE_IN_MINUTES = "sensorAgeInMinutes";
    public static final String DATE = "date";
    static final String TREND = "trend";
    static final String HISTORY = "history";


    private static final int numHistoryValues = 32;
    private static final int historyIntervalInMinutes = 15;
    private static final int numTrendValues = 16;

    @PrimaryKey
    private String id;
    private SensorData sensor;
    private int sensorAgeInMinutes = -1;
    public long date = -1;
    public RealmList<GlucoseData> trend = new RealmList<>();
    public RealmList<GlucoseData> history = new RealmList<>();

    public ReadingData() {}
    public ReadingData(RawTagData rawTagData) {
        id = rawTagData.id;
        date = rawTagData.date;
        sensor = new SensorData(rawTagData.sensor);

        sensorAgeInMinutes = rawTagData.getSensorAgeInMinutes();
        if (sensor.startDate < 0) {
            sensor.startDate = date - TimeUnit.MINUTES.toMillis(sensorAgeInMinutes);
        } else {
            // use start date of sensor to align data over multiple scans
            // (adding the magic number of 90 seconds to correct the time of the last trend value to be now)
            sensorAgeInMinutes = (int) TimeUnit.MILLISECONDS.toMinutes(
                    date - sensor.startDate + TimeUnit.SECONDS.toMillis(90));
        }

        int indexTrend = rawTagData.getIndexTrend();
        int indexHistory = rawTagData.getIndexHistory();

        // discrete version of the sensor age based on the history interval length to align data over multiple scans
        // (adding the magic number of 2 minutes to align the history values with the trend values)
        int sensorAgeDiscreteInMinutes = 2 +
                sensorAgeInMinutes - (sensorAgeInMinutes % historyIntervalInMinutes);
        //int sensorAgeDiscreteInMinutes = -4 + (int) (TimeUnit.MILLISECONDS.toMinutes(sensor.startDate) % historyIntervalInMinutes) +
        //        sensorAgeInMinutes - (sensorAgeInMinutes % historyIntervalInMinutes);

        // read history values from ring buffer, starting at indexHistory (bytes 124-315)
        for (int counter = 0; counter < numHistoryValues; counter++) {
            int index = (indexHistory + counter) % numHistoryValues;

            int glucoseLevelRaw = rawTagData.getHistoryValue(index);
            // skip zero values if the sensor has not filled the ring buffer completely
            if (glucoseLevelRaw > 0) {
                int dataAgeInMinutes = (numHistoryValues * historyIntervalInMinutes) -
                        counter * historyIntervalInMinutes;
                int ageInSensorMinutes = sensorAgeDiscreteInMinutes - dataAgeInMinutes;

                history.add(new GlucoseData(sensor, ageInSensorMinutes, glucoseLevelRaw, false));
            }
        }

        // read trend values from ring buffer, starting at indexTrend (bytes 28-123)
        for (int counter = 0; counter < numTrendValues; counter++) {
            int index = (indexTrend + counter) % numTrendValues;

            int glucoseLevelRaw = rawTagData.getTrendValue(index);
            // skip zero values if the sensor has not filled the ring buffer completely
            if (glucoseLevelRaw > 0) {
                int dataAgeInMinutes = numTrendValues - counter;
                int ageInSensorMinutes = sensorAgeInMinutes - dataAgeInMinutes;

                trend.add(new GlucoseData(sensor, ageInSensorMinutes, glucoseLevelRaw, true));
            }
        }
    }

}