package com.vritra.fetcher;

import com.vritra.fetcher.Fetcher;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.json.JSONObject;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.ListenableWorker.Result;
import androidx.work.Data;
import android.content.Context;
import android.app.DownloadManager;
import android.net.Uri;
import android.database.Cursor;
import android.os.Environment;
import android.widget.Toast;
import java.io.File;
import java.util.Random;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;



public class Downloader extends Worker {

    private CallbackContext callback=null;
    private DownloadManager downloadManager=null;
    private final String publicDirName=Environment.DIRECTORY_DOWNLOADS;
    private final JSONObject file=new JSONObject();

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
            final Uri uri=Uri.parse(url);

            final String extension=Fetcher.getExtension(url);
            String filename=null;
            String basename=props.optString("withBaseName",null);
            if(basename==null){
                filename=uri.getLastPathSegment();
                basename=filename.substring(0,filename.lastIndexOf("."));
            }
            else{
                filename=basename+"."+extension;
            }
            final String location=this.getLocationProp(props);
            final File sysfile=new File(location+"/"+filename);
            if(sysfile.exists()){
                final Boolean overwrite=props.optBoolean("overwrite",false);
                if(overwrite) sysfile.delete();
                else{
                    basename=basename+"_"+new Random().nextInt(10000);
                    filename=basename+"."+extension;
                }
            };
            final String fullpath="file://"+location+File.separator+filename;
            file.put("name",filename);
            file.put("location",location);
            file.put("fullpath",fullpath);

            this.downloadManager=(DownloadManager)Fetcher.cordova.getActivity().getSystemService(Context.DOWNLOAD_SERVICE);
            final DownloadManager.Request request=new DownloadManager.Request(uri);
            request.setTitle(filename);
            request.setMimeType(extension);
            request.setDescription("Downloading "+filename);
            final Boolean notify=props.optBoolean("notify",true);
            request.setNotificationVisibility(notify?DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED:DownloadManager.Request.VISIBILITY_HIDDEN);
            final Boolean saveToUserGallery=props.optBoolean("saveToUserGallery",false);
            file.put("savedToUserGallery",saveToUserGallery);
            if(saveToUserGallery) request.setDestinationInExternalPublicDir(this.publicDirName,File.separator+filename);
            else request.setDestinationUri(Uri.parse(fullpath));
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI|DownloadManager.Request.NETWORK_MOBILE);
            request.allowScanningByMediaScanner();

            final long downloadId=downloadManager.enqueue(request);
            new Thread(new Runnable(){
                public void run(){
                    try{
                        double progress=0;
                        final DownloadManager.Query query=new DownloadManager.Query();
                        query.setFilterById(downloadId);
                        while(progress<100){
                            Thread.sleep(50);
                            final Cursor cursor=downloadManager.query(query);
                            if(cursor.moveToFirst()){
                                final int downloadStatus=cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                                if(downloadStatus==DownloadManager.STATUS_FAILED){
                                    onFail(new Exception("Download failed"));
                                }
                                else{
                                    final long total=cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                                    if(total>0){
                                        final int downloaded=cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                                        progress=(double)(100*downloaded)/total;
                                    }
                                    onProgress(progress);
                                }
                            }
                            cursor.close();
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
        }
        catch(Exception exception){
            onFail(exception);
        }
    }

    private void onProgress(double progress) throws Exception {
        final Boolean isFinished=progress>=100;
        final JSONObject params=new JSONObject();
        params.put("progress",progress);
        params.put("isFinished",isFinished);
        if(isFinished){
            final String filename=file.optString("name");
            final String srcPath=Environment.getExternalStoragePublicDirectory(this.publicDirName).getPath()+File.separator+filename;
            final String dstPath=file.optString("location")+File.separator+filename;
            if((!file.optBoolean("savedToUserGallery"))||Downloader.copyFile(srcPath,dstPath)){
                final JSONObject entry=new JSONObject();
                entry.put("name",filename);
                entry.put("fullpath",file.optString("fullpath"));
                params.put("entry",entry);
            }
        };
        final PluginResult result=new PluginResult(PluginResult.Status.OK,params);
        result.setKeepCallback(!isFinished);
        callback.sendPluginResult(result);
    }

    private void onFail(Exception exception){
        try{
            this.downloadManager.remove();
            final JSONObject error=new JSONObject();
            error.put("message",exception.getMessage());
            callback.error(error);
        }
        catch(Exception e){}
    }

    String getLocationProp(JSONObject props){
        String location=props.optString("location",Fetcher.context.getExternalCacheDir().getPath());
        if(location.startsWith("file://")){
            location=location.substring(7);
        }
        return location;
    }

    static Boolean copyFile(String srcPath,String dstPath){
        try{
            return Downloader.copyFile(srcPath,dstPath,true);
        }
        catch(Exception exception){
            return true;
        }
    }
    static Boolean copyFile(String srcPath,String dstPath,Boolean reattempt) throws Exception {
        Boolean successful=false;
        FileInputStream inputStream=null;
        FileOutputStream outputStream=null;
        try{
            final File srcfile=new File(srcPath);
            final File dstfile=new File(dstPath);
            inputStream=new FileInputStream(srcfile);
            outputStream=new FileOutputStream(dstfile);
            final FileChannel inputChannel=inputStream.getChannel();
            final FileChannel outputChannel=outputStream.getChannel();
            inputChannel.transferTo(0,inputChannel.size(),outputChannel);
            successful=true;
        }
        catch(Exception exception){
            if(reattempt){
                successful=Downloader.copyFile(srcPath,dstPath,false);
            };
        }
        finally{
            inputStream.close();
            outputStream.close();
        }
        return successful;
    }
}
