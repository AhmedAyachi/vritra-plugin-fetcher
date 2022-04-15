

class Downloader:NSObject,URLSessionDelegate,URLSessionDataDelegate{
    
    var buffer:NSMutableData=NSMutableData();
    var size:Double=1;
    var progress:Double=0;
    let onProgress:(([AnyHashable:Any])->Void)?=nil;

    func download(_ url:URL,_ onProgress:(([AnyHashable:Any])->Void)?=nil){
        let sessionConfig=URLSessionConfiguration.default;
        let session=URLSession(configuration:sessionConfig,delegate:self,delegateQueue:OperationQueue.main);
        let request=URLRequest(url:url);
        let task=session.dataTask(with:request);
        //
        task.resume();
    }

    func urlSession(_ session:URLSession,dataTask:URLSessionDataTask,didReceive response:URLResponse,completionHandler:(URLSession.ResponseDisposition)->Void){
        size=Double(response.expectedContentLength);
        completionHandler(URLSession.ResponseDisposition.allow);
        print("Download size:",size);   
    }

    func urlSession(_ session:URLSession,dataTask:URLSessionDataTask,didReceive data:Data){
        buffer.append(data)
        let percentageDownloaded=Double(buffer.length)/size;
        progress=percentageDownloaded;
        print("Progress:",progress);
    }

    func urlSession(_ session:URLSession,task:URLSessionTask,didCompleteWithError error:Error?){
        progress=1.0;
        print("filename:",task.response?.suggestedFilename ?? false);
    }
}
