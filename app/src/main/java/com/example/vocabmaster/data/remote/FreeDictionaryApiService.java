package com.example.vocabmaster.data.remote;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface FreeDictionaryApiService {
    @GET("api/v2/entries/en/{word}")
    Call<List<DictionaryResponse>> getDefinition(@Path("word") String word);
}
