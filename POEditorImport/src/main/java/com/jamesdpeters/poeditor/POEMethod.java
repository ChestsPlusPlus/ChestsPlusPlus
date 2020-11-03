package com.jamesdpeters.poeditor;

import com.google.gson.Gson;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public abstract class POEMethod<T> {

    private static final OkHttpClient client = new OkHttpClient();
    private static final Gson gson = new Gson();

    FormBody.Builder requestBody = new FormBody.Builder()
            .add("api_token", POEapi.TOKEN);

    protected abstract String getBaseURL();
    protected abstract Class<T> getSerialiseClass();

    public void addPostData(String name, String value){
        requestBody.add(name, value);
    }

    public T get() {
        Request request = new Request.Builder()
                .url(getBaseURL())
                .post(requestBody.build())
                .build();

        try (Response response = client.newCall(request).execute()){
            if (!response.isSuccessful()) throw new IOException("Unexpected code "+ response);

            String terms = response.body().string();

            T t = gson.fromJson(terms, getSerialiseClass());
            return t;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
