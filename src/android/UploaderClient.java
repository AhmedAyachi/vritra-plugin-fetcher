package com.wurm.fetcher;

import retrofit2.Retrofit;
import okhttp3.OkHttpClient;
import retrofit2.converter.gson.GsonConverterFactory;


public class UploaderClient {
    protected static Retrofit retrofit=null;
    
    public static Retrofit getClient(String url){
        final OkHttpClient okhttpclient=new OkHttpClient();
        if(retrofit==null){
            retrofit=new Retrofit.Builder().baseUrl(url).
            addConverterFactory(GsonConverterFactory.create()).
            client(okhttpclient).build();
        }
        return retrofit;
    } 

}
