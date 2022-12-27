package com.ahmedayachi.fetcher;

import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.HeaderMap;
import retrofit2.http.Part;
import retrofit2.Call;
import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import java.util.ArrayList;
import java.util.Map;


public interface UploadAPI {

    @Multipart
    @POST("/")
    Call<ResponseBody> uploadFile(
        @HeaderMap Map<String,String> headers,
        @Part ArrayList<MultipartBody.Part> files,
        @Part ArrayList<MultipartBody.Part> fields
    );
}
