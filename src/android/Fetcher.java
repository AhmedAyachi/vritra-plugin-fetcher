package com.ahmedayachi.fetcher;

import com.ahmedayachi.fetcher.Downloader;
import com.ahmedayachi.fetcher.Uploader;
import org.apache.cordova.*;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import android.content.res.Resources;
import java.util.Random;
import androidx.work.WorkRequest;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Data;
import java.util.StringTokenizer;


public class Fetcher extends CordovaPlugin{

    static protected Context context;
    static protected CordovaInterface cordova;
    static protected Resources resources;
    static protected String packagename;
    static protected final JSONObject callbacks=new JSONObject();

    @Override
    public void initialize(CordovaInterface cordova,CordovaWebView webview){
        Fetcher.cordova=cordova;
        Fetcher.context=cordova.getContext();
        Fetcher.resources=Fetcher.context.getResources();
        Fetcher.packagename=Fetcher.context.getPackageName();
    }
    @Override
    public boolean execute(String action,JSONArray args,CallbackContext callbackContext) throws JSONException{
        if(action.equals("download")){
            JSONObject params=args.getJSONObject(0);
            this.fetch("download",params,callbackContext);
            return true;
        }
        else if(action.equals("upload")){
            JSONObject params=args.getJSONObject(0);
            this.fetch("upload",params,callbackContext);
            return true;
        }
        return false;
    }

    private void fetch(String method,JSONObject params,CallbackContext callbackContext){
        final String url=params.optString("url",null);
        if(url!=null){
            final String ref=Integer.toString(new Random().nextInt());
            final Data.Builder data=new Data.Builder();
            data.putString("callbackRef",ref);
            data.putString("params",params.toString());
            try{
                Fetcher.callbacks.put(ref,callbackContext);
            }
            catch(JSONException exception){}
            final WorkRequest request=new OneTimeWorkRequest.Builder(method.equals("download")?Downloader.class:Uploader.class).setInputData(data.build()).build();
            WorkManager.getInstance(Fetcher.context).enqueue(request);   
        }
    }

    static String getAppName(){
        String name=null;
        try{
            name=context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
        }
        catch(Exception exception){
            name="appname";
        }
        return name;
    }

    static String getExtension(String url){
        final StringTokenizer tokenizer=new StringTokenizer(url,".");
        String extension="";
        while(tokenizer.hasMoreTokens()){
            extension=tokenizer.nextToken();
        }
        return extension;
    }
    
    static protected int getResourceId(String type,String name){
        return resources.getIdentifier(name,type,Fetcher.packagename);
    }
}