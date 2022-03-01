package com.ahmedayachi.fetcher;

import com.ahmedayachi.fetcher.Fetcher;
import com.ahmedayachi.fetcher.UploadAPI;
import com.ahmedayachi.fetcher.UploaderClient;
import com.ahmedayachi.fetcher.FileUtils;
import com.ahmedayachi.fetcher.ProgressRequest;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import android.content.Intent;
import android.content.Context;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.ListenableWorker.Result;
import javafx.scene.media.Media;
import androidx.work.Data;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.NotificationCompat;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.widget.Toast;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Map;
import android.os.Build;
import android.net.Uri;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import retrofit2.Retrofit;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class Uploader extends Worker implements ProgressRequest.UploadCallbacks{

    static final String channelId="UploaderChannel";
    static Boolean channelCreated=false;
    protected static final NotificationManagerCompat manager=NotificationManagerCompat.from(Fetcher.context);

    private JSONObject props,params=new JSONObject(),error=new JSONObject();
    private CallbackContext callback;
    private NotificationCompat.Builder builder;
    private int id,index=-1,fileslength=0;
    private double unit;
    private Boolean trackEachFile=false;

    public Uploader(Context context,WorkerParameters params){
        super(context,params);
    }
    
   @Override
    public Result doWork(){
        final Data data=this.getInputData();
        final String callbackRef=data.getString("callbackRef");
        if(callbackRef!=null){
            try{
                callback=(CallbackContext)Fetcher.callbacks.opt(callbackRef);
                props=new JSONObject(data.getString("props"));
                trackEachFile=props.optBoolean("trackEachFile",false);
                this.upload();
                Fetcher.callbacks.remove(callbackRef);
            }
            catch(Exception exception){}
        }

        return Result.success();
    }

    private void upload(){
        final JSONArray files=props.optJSONArray("files");
        fileslength=files.length();
        if(fileslength>0){
            try{
                final ArrayList<MultipartBody.Part> fileParts=new ArrayList<MultipartBody.Part>(1);
                for(int i=0;i<fileslength;i++){
                    final JSONObject fileProps=files.optJSONObject(i);
                    final File file=new File(Uploader.getPath(fileProps.optString("path")));
                    final ProgressRequest fileRequest=new ProgressRequest(this,fileProps.optString("type","*"),file);
                    final MultipartBody.Part filePart=MultipartBody.Part.createFormData(props.optString("newFileNameKey","filename"),fileProps.optString("newName",file.getName()),fileRequest);
                    fileParts.add(filePart);
                }
                unit=100/fileslength;
                final Map<String,RequestBody> bodymap=new HashMap<String,RequestBody>();
                final JSONObject body=props.optJSONObject("body");
                if(body!=null){
                    final JSONArray keys=body.names();
                    if(keys!=null){
                        final int keyslength=keys.length();
                        for(int i=0;i<keyslength;i++){
                            final String key=keys.optString(i);
                            bodymap.put(key,RequestBody.create(MultipartBody.FORM,body.optString(key)));
                        }
                    }
                }
                final Retrofit client=UploaderClient.getClient(props.optString("url"));
                final UploadAPI api=client.create(UploadAPI.class);
                final Call call=api.uploadFile(fileParts,bodymap);
                call.enqueue(new Callback(){
                    @Override
                    public void onResponse(Call call,Response response){
                        if(response.isSuccessful()){
                            final String message=props.optString("toast",null);
                            if(message!=null){
                                Fetcher.cordova.getActivity().runOnUiThread(new Runnable(){
                                    public void run(){
                                        Toast.makeText(Fetcher.context,message,Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                            builder.setContentTitle(((fileslength>1)?""+fileslength+" Files":"File")+" uploaded successfully");
                            builder.setContentText(null);
                            builder.setProgress(100,100,false);
                            builder.setOngoing(false);
                            manager.notify(id,builder.build());
                            try{
                                params.put("progress",100);
                                params.put("isFinished",true);
                                params.put("response",getJSONObjectResponse(response));
                                callback.success(params);
                                manager.notify(id,builder.build());
                            }
                            catch(Exception exception){}
                        }
                        else{
                            try{
                                manager.cancel(id);
                                error.put("message","Unknown error");
                                error.put("response",getJSONObjectResponse(response));
                                callback.error(error);
                            }
                            catch(Exception exception){}
                        }
                    }
                    @Override
                    public void onFailure(Call call,Throwable throwable){
                        try{
                            manager.cancel(id);
                            error.put("message",throwable.getMessage());
                            callback.error(error);
                        }
                        catch(Exception e){}
                    }
                });
                this.showNotification();
            }
            catch(Exception exception){
                try{
                    error.put("message",exception.getMessage());
                    callback.error(error);
                }
                catch(Exception e){}
            }
        }
    }

    public void onFileStart(File file){
        index++;
        builder.setContentTitle("Uploading "+file.getName());
        manager.notify(id,builder.build());
    }

    @Override
    public void onFileProgress(int percentage){
        final int progress=(int)((index*unit)+((unit*percentage)/100));
        builder.setProgress(100,trackEachFile?percentage:progress,false);
        builder.setContentText((trackEachFile?percentage:progress)+"%");
        manager.notify(id,builder.build());
        if(progress<100){
            try{
                params.put("progress",progress);
                params.put("isFinished",false);
            }
            catch(Exception exception){}
            final PluginResult result=new PluginResult(PluginResult.Status.OK,params);
            result.setKeepCallback(true);
            callback.sendPluginResult(result);
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
        id=new Random().nextInt(9999);
        builder=new NotificationCompat.Builder(Fetcher.context,channelId);
        builder.setContentTitle((fileslength>1)?"Uploading "+fileslength+" files":"Uploading file");
        builder.setContentText("0%");
        builder.setSmallIcon(Fetcher.context.getApplicationInfo().icon);
        builder.setOngoing(true);
        builder.setOnlyAlertOnce(true);
        builder.setProgress(100,0,false);
        manager.notify(id,builder.build());
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
        if(string.startsWith(prefix)){
            path=string;
        }
        else{
            path=prefix+string;
        }
        path=FileUtils.getPath(Fetcher.context,Uri.parse(path));
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
