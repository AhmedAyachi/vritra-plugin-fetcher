package com.vritra.fetcher;

import com.vritra.common.*;
import com.vritra.fetcher.Fetcher;
import com.vritra.fetcher.UploadAPI;
import com.vritra.fetcher.UploaderClient;
import com.vritra.fetcher.ProgressRequest;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import android.content.Context;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.ListenableWorker.Result;
import androidx.work.Data;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.NotificationCompat;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.net.Uri;
import android.widget.Toast;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.Map;
import okhttp3.ResponseBody;
import okhttp3.MultipartBody;
import retrofit2.Retrofit;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class Uploader extends Worker implements ProgressRequest.UploadCallbacks {

    static final String channelId="VritraUploadChannel";
    static Boolean channelCreated=false;
    protected static final NotificationManagerCompat manager=NotificationManagerCompat.from(Fetcher.context);

    private JSONObject props,params=new JSONObject();
    private VritraError error=new VritraError();
    private final JSONArray files=new JSONArray();
    private final JSONArray excluded=new JSONArray();
    private CallbackContext callbackContext;
    private NotificationCompat.Builder notifBuilder;
    private int index=-1,filecount=0;
    private Integer notificationId=null;
    private double unit;
    private Boolean trackEachFile=false,notify=true;

    public Uploader(Context context,WorkerParameters params){
        super(context,params);
    }
    
   @Override
    public Result doWork(){
        final Data data=this.getInputData();
        final String callbackRef=data.getString("callbackRef");
        if(callbackRef!=null){
            try{
                this.callbackContext=(CallbackContext)Fetcher.callbacks.opt(callbackRef);
                this.props=new JSONObject(data.getString("props"));
                this.setFiles();
                this.filecount=files.length();
                this.unit=100/filecount;
                if(files.length()>0){
                    this.notify=props.optBoolean("notify",true);
                    if(this.notify){
                        this.trackEachFile=props.optBoolean("trackEachFile",false);
                    }
                    this.upload();
                }
                Fetcher.callbacks.remove(callbackRef);
            }
            catch(Exception exception){
                callbackContext.error(new VritraError(exception));
            }
        }
        return Result.success();
    }

    private void setFiles() throws Exception {
        final JSONArray files=props.optJSONArray("files");
        final int length=files.length();
        for(int i=0;i<length;i++){
            final JSONObject fileProps=files.optJSONObject(i);
            final File file=new File(Uploader.getPath(fileProps.optString("path")));
            if(file.exists()){
                fileProps.put("file",file);
                this.files.put(fileProps);
            }
            else{
                this.excluded.put(fileProps);
            }
        }
    }

    private void upload() throws Exception {
        final String url=props.optString("url");
        final Retrofit client=UploaderClient.getClient(url);
        final UploadAPI api=client.create(UploadAPI.class);
        final Call call=api.uploadFiles(url,this.getHeaders(),this.getFileParts(),this.getFieldParts());
        call.enqueue(new Callback(){
            @Override
            public void onResponse(Call call,Response response){
                try{
                    params.put("isFinished",true);
                    if(response.isSuccessful()){
                        final String toastmsg=props.optString("toast",null);
                        if(toastmsg!=null){
                            Fetcher.cordova.getActivity().runOnUiThread(new Runnable(){
                                public void run(){
                                    Toast.makeText(Fetcher.context,toastmsg,Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                        if(notify){
                            notifBuilder.setContentTitle(((filecount>1)?""+filecount+" Files":"File")+" uploaded successfully");
                            notifBuilder.setContentText(null);
                            notifBuilder.setProgress(100,100,false);
                            notifBuilder.setOngoing(false);
                            manager.notify(notificationId,notifBuilder.build());
                        }
                        params.put("progress",100);
                        params.put("notificationId",notificationId);
                        params.put("response",getJSONObjectResponse(response));
                        params.put("excluded",excluded.length()>0?excluded:null);
                        callbackContext.success(params);
                        if(notify) manager.notify(notificationId,notifBuilder.build());
                    }
                    else{
                        if(notify) manager.cancel(notificationId.intValue());
                        final VritraError error=new VritraError("Unknown error");
                        error.set("response",getJSONObjectResponse(response));
                        callbackContext.error(error);
                    }
                }
                catch(Exception exception){
                    callbackContext.error(new VritraError(exception.getMessage()));
                }
            }
            @Override
            public void onFailure(Call call,Throwable throwable){
                if(notify) manager.cancel(notificationId.intValue());
                error.set("message",throwable.getMessage());
                callbackContext.error(error);
            }
        });
        if(this.notify){
            this.showNotification();
        }
    }

    private Map<String,String> getHeaders(){
        final Map<String,String> headers=new HashMap<String,String>();
        final JSONObject header=props.optJSONObject("header");
        if(header!=null){
            final Iterator<String> keys=header.keys();
            while(keys.hasNext()){
                final String key=keys.next();
                headers.put(key,header.optString(key));
            }
        }

        return headers;
    }

    private ArrayList<MultipartBody.Part> getFileParts(){
        final ArrayList<MultipartBody.Part> fileParts=new ArrayList<MultipartBody.Part>(0);
        final int length=files.length();
        for(int i=0;i<length;i++){
            final JSONObject fileProps=files.optJSONObject(i);
            final File file=(File)fileProps.opt("file");
            String type=null;
            try{
                type=fileProps.optString("type",Files.probeContentType(file.toPath()));
            }
            catch(Exception exception){
                type="*/*";
            }
            final ProgressRequest fileRequest=new ProgressRequest(this,type,file);
            final String key=fileProps.optString("key","file"+i);
            final String filename=fileProps.optString("withBaseName",file.getName());
            final MultipartBody.Part filePart=MultipartBody.Part.createFormData(key,filename,fileRequest);
            fileParts.add(filePart);
        }
        return fileParts;
    }

    private ArrayList<MultipartBody.Part> getFieldParts(){
        final ArrayList<MultipartBody.Part> fieldParts=new ArrayList<MultipartBody.Part>(0);
        final JSONObject body=props.optJSONObject("body");
        if(body!=null){
            final JSONArray keys=body.names();
            if(keys!=null){
                final int keyslength=keys.length();
                for(int i=0;i<keyslength;i++){
                    final String key=keys.optString(i);
                    final String value=body.optString(key);
                    final MultipartBody.Part fieldPart=MultipartBody.Part.createFormData(key,value);
                    fieldParts.add(fieldPart);
                }
            }
        }
        return fieldParts;
    }

    public void onFileStart(File file){
        index++;
        if(this.notify){
            notifBuilder.setContentTitle("Uploading "+file.getName());
            manager.notify(notificationId,notifBuilder.build());
        }
    }

    @Override
    public void onFileProgress(int percentage){
        final int progress=(int)((index*unit)+((unit*percentage)/100));
        if(this.notify){
            notifBuilder.setProgress(100,trackEachFile?percentage:progress,false);
            notifBuilder.setContentText((trackEachFile?percentage:progress)+"%");
            manager.notify(notificationId,notifBuilder.build());
        }
        if(progress<100){
            try{
                params.put("progress",progress);
                params.put("isFinished",false);
            }
            catch(Exception exception){}
            final PluginResult result=new PluginResult(PluginResult.Status.OK,params);
            result.setKeepCallback(true);
            callbackContext.sendPluginResult(result);
        }
    }

    @Override
    public void onFileFinish(File file){
        
    } 

    @Override
    public void onFileError(){
    
    }

    private void showNotification(){
        Uploader.createNotificationChannel();
        notificationId=new Random().nextInt(9999);
        notifBuilder=new NotificationCompat.Builder(Fetcher.context,channelId);
        notifBuilder.setContentTitle((filecount>1)?"Uploading "+(filecount+excluded.length())+" files":"Uploading file");
        notifBuilder.setContentText("0%");
        notifBuilder.setSmallIcon(Fetcher.context.getApplicationInfo().icon);
        notifBuilder.setOngoing(true);
        notifBuilder.setOnlyAlertOnce(true);
        notifBuilder.setAutoCancel(true);
        notifBuilder.setProgress(100,0,false);
        notifBuilder.setPriority(NotificationCompat.PRIORITY_DEFAULT);
        notifBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        manager.notify(notificationId,notifBuilder.build());
    }

    static private JSONObject getJSONObjectResponse(Response response) throws Exception{
        String json=response.toString().replace("Response{","{").replaceAll("=",":\"").replaceAll(",","\",").replace("}","\"}");
        final JSONObject object=new JSONObject(json);
        final Boolean isSuccessful=response.isSuccessful();
        object.put("isSuccessful",isSuccessful);
        final String code=object.optString("code",null);
        if(code!=null){
            object.put("code",Integer.parseInt(code));
        }
        final ResponseBody responsebody=isSuccessful?((ResponseBody)response.body()):response.errorBody();
        final String query=responsebody.string();
        try{
            final JSONObject body=new JSONObject(query);
            object.put("body",body);
        }
        catch(JSONException exception){
            object.put("body",query);
        }
        return object;
    }

    static private String getPath(String string){
        String path="";
        final String prefix="file:///";
        if(string.startsWith(prefix)) path=string;
        else path=prefix+string;
        path=FileFinder.getUriPath(Fetcher.context,path);
        return path;
    }

    static private void createNotificationChannel(){
        if((!channelCreated)&&(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)){
            int importance=NotificationManager.IMPORTANCE_HIGH;
            final NotificationChannel channel=new NotificationChannel(channelId,channelId,importance);
            channel.setDescription(channelId);
            NotificationManager notificationManager=Fetcher.cordova.getActivity().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            channelCreated=true;
        }
    }
}
