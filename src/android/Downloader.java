package com.corella.fetcher;

import java.io.File;

import com.corella.fetcher.Fetcher;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONObject;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.ListenableWorker.Result;
import androidx.work.Data;
import android.content.Context;
import android.content.Intent;
import android.app.DownloadManager;
import android.net.Uri;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.Environment;
import android.database.Cursor;
import android.widget.Toast;


public class Downloader extends Worker{

    private CallbackContext callback=null;

    public Downloader(Context context,WorkerParameters params){
        super(context,params);
    }

    @Override
    public Result doWork(){
        Boolean isFulfilled=false;
        final Data data=this.getInputData();
        final String callbackRef=data.getString("callbackRef");
        if(callbackRef!=null){
            try{
                this.callback=(CallbackContext)Fetcher.callbacks.opt(callbackRef);
                final JSONObject props=new JSONObject(data.getString("props"));
                this.download(props);
                isFulfilled=true;
                Fetcher.callbacks.remove(callbackRef);
            }
            catch(Exception exception){}
        }

        return isFulfilled?Result.success():Result.failure();
    }

    private void download(JSONObject props){
        try{
            final String url=props.optString("url");
            final DownloadManager downloader=(DownloadManager)Fetcher.cordova.getActivity().getSystemService(Context.DOWNLOAD_SERVICE);
            final Uri uri=Uri.parse(url);
            final DownloadManager.Request request=new DownloadManager.Request(uri);
            final String extension=Fetcher.getExtension(url);
            final String filename=props.optString("filename",Fetcher.getAppName().replaceAll(" ",""))+"."+extension;
            request.setTitle(filename);
            final String type=props.optString("type",extension);
            request.setMimeType(type);
            request.setDescription("Downloding");
            final Boolean notify=props.optBoolean("notify",true);
            request.setNotificationVisibility(notify?DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED:DownloadManager.Request.VISIBILITY_HIDDEN);
            final String location=props.optString("location",null);
            if(location!=null){
                request.setDestinationUri(Uri.parse(location+"/"+filename));
            }
            else{
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"/"+filename);
            }
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI|DownloadManager.Request.NETWORK_MOBILE);
            final Boolean overwrite=props.optBoolean("overwrite",false);
            if(overwrite){
                Downloader.deleteExistingFile(location,filename);
            }

            final long downloadId=downloader.enqueue(request);
            final JSONObject params=new JSONObject();
            try{
                params.put("progress",0);
                params.put("isFinished",false);
            }
            catch(Exception exception){};
            new Thread(new Runnable(){
                public void run(){
                    try{
                        Boolean isFinished=false;
                        final DownloadManager.Query query=new DownloadManager.Query();
                        query.setFilterById(downloadId);
                        while(!isFinished){
                            final Cursor cursor=downloader.query(query);
                            if(cursor.moveToFirst()){
                                final long total=cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                                if(total>0){
                                    final int downloaded=cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                                    final double progress=(double)(100*downloaded)/total;
                                    isFinished=progress>=100;
                                    params.put("progress",progress);
                                    params.put("isFinished",isFinished);
                                    final PluginResult result=new PluginResult(PluginResult.Status.OK,params);
                                    result.setKeepCallback(!isFinished);
                                    callback.sendPluginResult(result);
                                }
                            }
                            cursor.close();
                            Thread.sleep(100);
                        }

                        final String toast=props.optString("toast",null);
                        if(toast!=null){
                            Fetcher.cordova.getActivity().runOnUiThread(new Runnable(){
                                public void run(){
                                    Toast.makeText(Fetcher.context,toast,Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
                    catch(Exception exception){
                        onFail(exception);
                    };
                }   
            }).start();
            /* Fetcher.context.registerReceiver(new BroadcastReceiver(){
                @Override
                public void onReceive(Context context,Intent intent){
                    try{
                        params.put("isFinished",true);
                        params.put("progress",100);
                        callback.success(params);
                    }
                    catch(Exception exception){}
                }
            },new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)); */
        }
        catch(Exception exception){
            onFail(exception);
        }
    }

    private void onFail(Exception exception){
        try{
            final JSONObject error=new JSONObject();
            error.put("message",exception.getMessage());
            callback.error(error);
        }
        catch(Exception e){}
    }

    static void deleteExistingFile(String location,String filename){
        if(location==null){
            location=Environment.getExternalStorageDirectory().getPath()+"/"+Environment.DIRECTORY_DOWNLOADS;
        }
        if(location.startsWith("file://")){
            location=location.substring(7);
        }
        final File file=new File(location+"/"+filename);
        if(file.exists()){
            file.delete();
        }
    }
}
