package com.ahmedayachi.fetcher;

import okhttp3.RequestBody;
import okhttp3.MediaType;
import java.io.FileInputStream;
import android.os.Handler;
import android.os.Looper;
import okio.BufferedSink;
import java.lang.Runnable;
import java.io.File;
import java.io.IOException;


public class ProgressRequest extends RequestBody{

    private File file;
    private String type;
    private Boolean autoDelete=false;
    int index,length=1;
    private UploadCallbacks listener;
    
    private static final int DEFAULT_BUFFER_SIZE=2048;
    public ProgressRequest(String type,File file,UploadCallbacks listener,int index,int length){
        this.type=type;
        this.file=file;
        this.listener=listener;
        this.index=index;
        this.length=length;
    }
    
    @Override
    public MediaType contentType(){
        return MediaType.parse(type.contains("/")?type:type+"/*");
    }
    
    @Override
    public long contentLength() throws IOException{
      return file.length();
    }
    
    @Override
    public void writeTo(BufferedSink sink) throws IOException{
        final byte[] buffer=new byte[DEFAULT_BUFFER_SIZE];
        final FileInputStream inputstream=new FileInputStream(file);
        long uploaded=0;
        try{
            int read;
            final Handler handler=new Handler(Looper.getMainLooper());
            final long fileSize=file.length();
            final double unit=100/length;
            while((read=inputstream.read(buffer))!=-1){
                final int progress=(int)((index*unit)+(unit*uploaded/fileSize));
                handler.post(new Runnable(){
                    public void run(){
                        listener.onProgress(progress);            
                    }
                });
                uploaded+=read;
                sink.write(buffer,0,read);
            }
        }
        finally{
            inputstream.close();
        }
    }

    public interface UploadCallbacks{
        void onProgress(int progress);
        void onError();
        void onFinish();
    }
}
