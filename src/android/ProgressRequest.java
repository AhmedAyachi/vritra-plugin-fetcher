package com.vritra.fetcher;

import okhttp3.RequestBody;
import okhttp3.MediaType;
import java.io.FileInputStream;
import android.os.Handler;
import android.os.Looper;
import okio.BufferedSink;
import java.lang.Runnable;
import java.io.File;
import java.io.IOException;


public class ProgressRequest extends RequestBody {

    private File file;
    private String type;
    private Boolean autoDelete=false;
    private UploadCallbacks listener;
    
    private static final int bufferSize=2048;
    public ProgressRequest(UploadCallbacks listener,String type,File file){
        this.type=type;
        this.file=file;
        this.listener=listener;
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
        final byte[] buffer=new byte[bufferSize];
        final FileInputStream inputstream=new FileInputStream(file);
        long uploaded=0;
        listener.onFileStart(file);
        try{
            int read;
            final long fileSize=file.length();
            while((read=inputstream.read(buffer))!=-1){
                final int progress=(int)(100*uploaded/fileSize);
                listener.onFileProgress(progress);
                uploaded+=read;
                sink.write(buffer,0,read);
            }
            listener.onFileFinish(file);
        }
        finally{
            inputstream.close();
        }
    }

    public interface UploadCallbacks{
        void onFileStart(File file);
        void onFileProgress(int progress);
        void onFileFinish(File file);
        void onFileError();
    }
}
