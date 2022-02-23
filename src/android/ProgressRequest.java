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
    private String mPath;
    private UploadCallbacks listener;
    private String type;
    int index,length=1;

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
        return MediaType.parse(type+"/*");
    }
    
    @Override
    public long contentLength() throws IOException{
      return file.length();
    }
    
    @Override
    public void writeTo(BufferedSink sink) throws IOException{
        long fileLength=file.length();
        byte[] buffer=new byte[DEFAULT_BUFFER_SIZE];
        final FileInputStream inputstream=new FileInputStream(file);
        long uploaded=0;
        try{
            int read;
            final Handler handler=new Handler(Looper.getMainLooper());
            while((read=inputstream.read(buffer))!=-1){
                handler.post(new ProgressUpdater(uploaded,fileLength,index,length));
                uploaded+=read;
                sink.write(buffer,0,read);
            }
        }
        finally{
            inputstream.close();
        }
    }
    
    private class ProgressUpdater implements Runnable{
        private long uploaded;
        private long total;
        private int index,length;
        public ProgressUpdater(long uploaded, long total,int index,int length){
            this.uploaded=uploaded;
            this.total=total;
            this.index=index;
            this.length=length;
        }

        @Override
        public void run() {
            final double unit=100/this.length;
            final int progress=(int)((this.index*unit)+(unit*this.uploaded/this.total));
            listener.onProgress(progress);            
        }
    }

    public interface UploadCallbacks{
        void onProgress(int progress);
        void onError();
        void onFinish();
    }
}
