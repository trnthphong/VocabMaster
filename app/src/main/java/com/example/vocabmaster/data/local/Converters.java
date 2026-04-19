package com.example.vocabmaster.data.local;

import androidx.room.TypeConverter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class Converters {
    private static final Gson gson = new Gson();

    @TypeConverter
    public static Date fromTimestamp(Long value) {
        return value == null ? null : new Date(value);
    }

    @TypeConverter
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }

    @TypeConverter
    public static String fromIntegerList(List<Integer> list) {
        if (list == null) return null;
        return gson.toJson(list);
    }

    @TypeConverter
    public static List<Integer> toIntegerList(String value) {
        if (value == null) return null;
        Type listType = new TypeToken<List<Integer>>() {}.getType();
        return gson.fromJson(value, listType);
    }

    @TypeConverter
    public static String fromStringList(List<String> list) {
        if (list == null) return null;
        return gson.toJson(list);
    }

    @TypeConverter
    public static List<String> toStringList(String value) {
        if (value == null) return null;
        Type listType = new TypeToken<List<String>>() {}.getType();
        return gson.fromJson(value, listType);
    }

    @TypeConverter
    public static String fromSkillScoresMap(Map<String, Double> map) {
        if (map == null) return null;
        return gson.toJson(map);
    }

    @TypeConverter
    public static Map<String, Double> toSkillScoresMap(String value) {
        if (value == null) return null;
        Type mapType = new TypeToken<Map<String, Double>>() {}.getType();
        return gson.fromJson(value, mapType);
    }
}
