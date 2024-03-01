package com.vritra.fetcher;

import com.vritra.common.*;
import org.apache.cordova.*;
import com.vritra.fetcher.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import androidx.work.WorkRequest;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Data;
import java.util.StringTokenizer;
import java.util.Random;


public class Fetcher extends VritraPlugin {

    static protected CordovaInterface cordova;
    static protected final JSONObject callbacks=new JSONObject();

    @Override
    public void initialize(CordovaInterface cordova,CordovaWebView webview){
        super.initialize(cordova,webview);
        Fetcher.cordova=cordova;
    }
    @Override
    public boolean execute(String action,JSONArray args,CallbackContext callbackContext) throws JSONException{
        if(action.equals("download")){
            JSONObject props=args.getJSONObject(0);
            this.fetch("download",props,callbackContext);
            return true;
        }
        else if(action.equals("upload")){
            JSONObject props=args.getJSONObject(0);
            this.fetch("upload",props,callbackContext);
            return true;
        }
        return false;
    }

    private void fetch(String method,JSONObject props,CallbackContext callbackContext){
        final String url=props.optString("url",null);
        if(url!=null){
            final String ref=Integer.toString(new Random().nextInt());
            final Data.Builder data=new Data.Builder();
            data.putString("callbackRef",ref);
            data.putString("props",props.toString());
            try{
                Fetcher.callbacks.put(ref,callbackContext);
            }
            catch(JSONException exception){}
            final WorkRequest request=new OneTimeWorkRequest.Builder(method.equals("download")?Downloader.class:Uploader.class).setInputData(data.build()).build();
            WorkManager.getInstance(Fetcher.context).enqueue(request);   
        }
        else{
            try{
                final JSONObject error=new JSONObject();
                error.put("message","url attribute is required");
                callbackContext.error(error);
            }
            catch(Exception e){}
        }
    }

    static String getExtension(String url){
        final int urlParamIndex=url.lastIndexOf("?");
        final int pointIndex=url.lastIndexOf(".");
        return urlParamIndex>pointIndex?url.substring(pointIndex+1,urlParamIndex):url.substring(pointIndex+1);
    }
}