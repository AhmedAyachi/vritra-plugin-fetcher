package com.ahmedayachi.fetcher;

import com.ahmedayachi.fetcher.Fetcher;
import com.ahmedayachi.fetcher.UploadAPI;
import com.ahmedayachi.fetcher.UploaderClient;
import com.ahmedayachi.fetcher.FileUtils;
import com.ahmedayachi.fetcher.ProgressRequest;
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
import android.os.Build;
import android.net.Uri;
import okhttp3.ResponseBody;
import okhttp3.MultipartBody;
import retrofit2.Retrofit;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class Uploader extends Worker implements ProgressRequest.UploadCallbacks {

    static final String channelId="FetcherUploaderChannel";
    static Boolean channelCreated=false;
    protected static final NotificationManagerCompat manager=NotificationManagerCompat.from(Fetcher.context);

    private JSONObject props,params=new JSONObject(),error=new JSONObject();
    private final JSONArray files=new JSONArray();
    private final JSONArray excluded=new JSONArray();
    private CallbackContext callback;
    private NotificationCompat.Builder builder;
    private int id,index=-1,filecount=0;
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
                this.callback=(CallbackContext)Fetcher.callbacks.opt(callbackRef);
                this.props=new JSONObject(data.getString("props"));
                this.setFiles();
                this.filecount=files.length();
                this.unit=100/filecount;
                if(this.files.length()>0){
                    this.notify=props.optBoolean("notify",true);
                    if(this.notify){
                        this.trackEachFile=props.optBoolean("trackEachFile",false);
                    }
                    this.upload();
                }
                Fetcher.callbacks.remove(callbackRef);
            }
            catch(Exception exception){
                try{
                    error.put("message",exception.getMessage());
                    callback.error(error);
                }
                catch(Exception e){}
            }
        }
        return Result.success();
    }

    private void setFiles(){
        final JSONArray files=props.optJSONArray("files");
        final int length=files.length();
        for(int i=0;i<length;i++){
            final JSONObject fileProps=files.optJSONObject(i);
            final File file=new File(Uploader.getPath(fileProps.optString("path")));
            try{
                if(file.exists()){
                    fileProps.put("file",file);
                    this.files.put(fileProps);
                }
                else{
                    this.excluded.put(fileProps);
                }
            }
            catch(Exception exception){}
        }
    }

    private void upload() throws Exception {
        final Retrofit client=UploaderClient.getClient(props.optString("url"));
        final UploadAPI api=client.create(UploadAPI.class);
        final Call call=api.uploadFile(this.getHeaders(),this.getFileParts(),this.getFieldParts());
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
                    builder.setContentTitle(((filecount>1)?""+filecount+" Files":"File")+" uploaded successfully");
                    builder.setContentText(null);
                    builder.setProgress(100,100,false);
                    builder.setOngoing(false);
                    manager.notify(id,builder.build());
                    try{
                        params.put("progress",100);
                        params.put("isFinished",true);
                        params.put("response",getJSONObjectResponse(response));
                        params.put("excluded",excluded.length()>0?excluded:null);
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
            final String filename=fileProps.optString("name",file.getName());
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
            builder.setContentTitle("Uploading "+file.getName());
            manager.notify(id,builder.build());
        }
    }

    @Override
    public void onFileProgress(int percentage){
        final int progress=(int)((index*unit)+((unit*percentage)/100));
        if(this.notify){
            builder.setProgress(100,trackEachFile?percentage:progress,false);
            builder.setContentText((trackEachFile?percentage:progress)+"%");
            manager.notify(id,builder.build());
        }
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
        builder.setContentTitle((filecount>1)?"Uploading "+(filecount+excluded.length())+" files":"Uploading file");
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
