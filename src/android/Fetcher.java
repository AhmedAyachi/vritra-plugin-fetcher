package com.ahmedayachi.fetcher;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;


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
                WebView.callbacks.put(ref,callbackContext);
            }
            catch(JSONException exception){}
            final WorkRequest request=new OneTimeWorkRequest.Builder(method.equals("download")?Downloader.class:Uploader.class).setInputData(data.build()).build();
            WorkManager.getInstance(WebView.context).enqueue(request);   
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