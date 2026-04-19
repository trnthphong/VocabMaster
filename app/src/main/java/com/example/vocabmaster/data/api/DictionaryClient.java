package com.example.vocabmaster.data.api;

import com.example.vocabmaster.data.remote.FreeDictionaryApiService;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class DictionaryClient {
    private static final String BASE_URL = "https://api.dictionaryapi.dev/";
    private static Retrofit retrofit = null;

    public static FreeDictionaryApiService getService() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(FreeDictionaryApiService.class);
    }
}
