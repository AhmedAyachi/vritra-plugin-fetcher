package com.ahmedayachi.fetcher;

import com.ahmedayachi.fetcher.Fetcher;
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
                final CallbackContext callback=(CallbackContext)Fetcher.callbacks.opt(callbackRef);
                final JSONObject params=new JSONObject(data.getString("params"));
                this.download(params,callback);
                isFulfilled=true;
                Fetcher.callbacks.remove(callbackRef);
            }
            catch(Exception exception){}
        }

        return isFulfilled?Result.success():Result.failure();
    }

    private void download(JSONObject params,CallbackContext callback){
        try{
            final String url=params.optString("url");
            final DownloadManager downloader=(DownloadManager)Fetcher.cordova.getActivity().getSystemService(Context.DOWNLOAD_SERVICE);
            final Uri uri=Uri.parse(url/* .replaceAll(" ","%20") */);
            final DownloadManager.Request request=new DownloadManager.Request(uri);
            final String extension=Fetcher.getExtension(url);
            final String filename=params.optString("filename",Fetcher.getAppName().replaceAll(" ",""))+"."+extension;
            request.setTitle(filename);
            final String type=params.optString("type");
            if(type!=null){
                request.setMimeType(type);
            }
            else{
                request.setMimeType(extension);
            }
            request.setDescription("Downloding");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            final String location=params.optString("location",null);
            if(location!=null){
                request.setDestinationUri(Uri.parse(location+"/"+filename));
            }
            else{
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"/"+filename);
            }
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI|DownloadManager.Request.NETWORK_MOBILE);
            
            
            final long downloadId=downloader.enqueue(request);
            final JSONObject args=new JSONObject();
            try{
                args.put("progress",0);
                args.put("isFinished",false);
            }
            catch(Exception exception){};
            new Thread(new Runnable() {
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
                                    final double progress=(downloaded/total)*100f;
                                    isFinished=progress>=100;
                                    args.put("progress",progress);
                                    args.put("isFinished",isFinished);
                                    final PluginResult result=new PluginResult(PluginResult.Status.OK,args);
                                    result.setKeepCallback(!isFinished);
                                    callback.sendPluginResult(result);
                                }
                            }
                            cursor.close();
                            Thread.sleep(100);
                        }
                        final String toast=params.optString("toast",null);
                        if(toast!=null){
                            Fetcher.cordova.getActivity().runOnUiThread(new Runnable(){
                                public void run(){
                                    Toast.makeText(Fetcher.context,toast,Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
                    catch(Exception exception){
                        callback.error(exception.getMessage());
                    };
                }   
            }).start();
            /* Fetcher.context.registerReceiver(new BroadcastReceiver(){
                @Override
                public void onReceive(Context context,Intent intent){
                    try{
                        args.put("isFinished",true);
                        args.put("progress",100);
                        callback.success(args);
                    }
                    catch(Exception exception){}
                }
            },new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)); */
        }
        catch(Exception exception){
            callback.error(exception.getMessage());
        }
    }
}
