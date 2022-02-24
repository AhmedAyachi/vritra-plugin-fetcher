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

    CallbackContext callback;
    JSONObject output=new JSONObject();
    JSONObject error=new JSONObject();
    NotificationCompat.Builder builder;
    int id;

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
                final JSONObject params=new JSONObject(data.getString("params"));
                this.upload(params);
                Fetcher.callbacks.remove(callbackRef);
            }
            catch(Exception exception){}
        }

        return Result.success();
    }

    private void upload(JSONObject params){
        final JSONArray files=params.optJSONArray("files");
        final ArrayList<MultipartBody.Part> fileParts=new ArrayList<MultipartBody.Part>(1);
        try{
            final int fileslength=files.length();
            for(int i=0;i<fileslength;i++){
                final JSONObject props=files.optJSONObject(i);
                final File file=new File(FileUtils.getPath(Fetcher.context,Uri.parse(props.optString("path"))));
                final ProgressRequest fileRequest=new ProgressRequest(props.optString("type","*"),file,this,i,fileslength);
                final MultipartBody.Part filePart=MultipartBody.Part.createFormData(params.optString("newFileNameKey","filename"),props.optString("newName",file.getName()),fileRequest);
                fileParts.add(filePart);
            }
            
            final Map<String,RequestBody> bodymap=new HashMap<String,RequestBody>();
            final JSONObject body=params.optJSONObject("body");
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
            final Retrofit client=UploaderClient.getClient(params.optString("url"));
            final UploadAPI api=client.create(UploadAPI.class);
            final Call call=api.uploadFile(fileParts,bodymap);
            call.enqueue(new Callback(){
                @Override
                public void onResponse(Call call,Response response){
                    if(response.isSuccessful()){
                        final String message=params.optString("toast",null);
                        if(message!=null){
                            Fetcher.cordova.getActivity().runOnUiThread(new Runnable(){
                                public void run(){
                                    Toast.makeText(Fetcher.context,message,Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                        builder.setContentTitle(((fileslength>1)?""+fileslength+" files":"file")+" uploaded successfully");
                        builder.setContentText(null);
                        builder.setProgress(100,100,false);
                        builder.setOngoing(false);
                        manager.notify(id,builder.build());
                        try{
                            output.put("progress",100);
                            output.put("isFinished",true);
                            output.put("response",getJSONObjectResponse(response));
                            callback.success(output);
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
        catch(Exception exception){
            try{
                error.put("message",exception.getMessage());
                callback.error(error);
            }
            catch(Exception e){}
        }
    }

    @Override
    public void onProgress(int progress){ 
        final Boolean isFinished=progress>=100;
        try{
            output.put("progress",progress);
            output.put("isFinished",isFinished);
        }
        catch(Exception exception){}
        builder.setProgress(100,progress,false);
        builder.setContentText(progress+"%");
        manager.notify(id,builder.build());
        final PluginResult result=new PluginResult(PluginResult.Status.OK,output);
        result.setKeepCallback(!isFinished);
        callback.sendPluginResult(result);
    }
    @Override
    public void onError(){
        try{
            error.put("message","Unknown error");
            callback.error(error);
        }
        catch(Exception exception){}
    }
    @Override
    public void onFinish(){
        
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
