

class Downloader:NSObject,URLSessionDelegate,URLSessionDownloadDelegate{
    
    static let appname=Bundle.main.infoDictionary?["CFBundleDisplayName" as String] as? String ?? "";
    let props:[AnyHashable:Any];
    var location:URL;
    var filename:String="";
    var onProgress:(([AnyHashable:Any])->Void)?=nil;
    var onFail:((String)->Void)?=nil;

    init(_ props:[AnyHashable:Any]){
        self.props=props;
        setLocation();
    }

    func download(onProgress:(([AnyHashable:Any])->Void)?=nil,onFail:((String)->Void)?=nil){
        if let url=props["url"] as? URL {
            let sessionConfig=URLSessionConfiguration.default;
            let session=URLSession(
                configuration:sessionConfig,
                delegate:self,
                delegateQueue:nil
            );
            let request=URLRequest(url:url);
            let task=session.downloadTask(with:request);
            self.onProgress=onProgress;
            self.onFail=onFail;
            task.resume();
        }
    }

    private func setLocation(){
        if let path=props["location"] as? String && !path.isEmpty{
            self.location=URL(fileURLWithPath:path);
        }
        else{
            self.location=try? FileManager.default.url(
                for:.documentDirectory,
                in:.userDomainMask,
                appropriateFor:nil,
                create:false
            )
        }
    }

    private func setFileName(){
        if let name=props["filename"] as? String{
            self.filename=name;
        }
    }
    
    func urlSession(_ session:URLSession,downloadTask:URLSessionDownloadTask,didWriteData:Int64,totalBytesWritten:Int64,totalBytesExpectedToWrite:Int64){
        if !(onProgress==nil){
            let params:[String:Any]=[
                "isFinished":false,
                "progress":Double(totalBytesWritten)/Double(totalBytesExpectedToWrite),
            ];
            onProgress!(params);
        } 
    }

    func urlSession(_ session:URLSession,downloadTask task:URLSessionDownloadTask,didFinishDownloadingTo location:URL){
        do{
            try self.saveFile(location,task,session);
            if !(onProgress==nil){
                let params:[String:Any]=[
                    "isFinished":true,
                    "progress":1,
                ];
                onProgress!(params);
                print("location:",location.path);
            }
        }
        catch{
            self.urlSession(session,didBecomeInvalidWithError:error);
        }
    }

    func urlSession(_ session:URLSession,didBecomeInvalidWithError:Error?){
        if let error=didBecomeInvalidWithError as? Error,!(onFail==nil){
            onFail!(error.localizedDescription);
        }
    }
    
    private func saveFile(_ path:URL,_ task:URLSessionDownloadTask,_ session:URLSession)throws{
        if let location=self.location as? URL,let response=task.response as? URLResponse {
            var ext:String=Downloader.getExtension(response);
            var filename:String=props["filename"] as? String ?? "";
            if(filename.isEmpty){
                filename=response.suggestedFilename ?? "\(Downloader.appname).\(ext)";
            }
            else{
                filename="\(filename).\(ext)";
            }
            
            let uri=target.appendingPathComponent(filename);
            try FileManager.default.moveItem(at:path,to:uri);
            filename=response.suggestedFilename ?? "";
        }
        else{
            self.urlSession(session,didBecomeInvalidWithError:Fetcher.Error("Request Response is undefined"));
        }
    }

    static func getURLExtension(_ url:URL)->String{
        let name:String=url.lastPathComponent;
        return Downloader.getExtension(name);
    }

    static func getExtension(_ path:String,_ separator:String=".")->String{
        let parts=path.split(separator:separator);
        var ext=parts.count>1 ? String(parts.last) :"";
        if(ext.isEmpty){
            ext="tmp";
        }
        return ext;
    }

    static func getExtension(_ respone:URLResponse)->String{
        var ext="";
        if let mimetype=respone.mimetype as? String {
            ext=Downloader.getExtension(mimetype,"/");

        }
        if ext.isEmpty,let name=respone.suggestedFilename as? String {
            ext=Downloader.getExtension(name,".");
        }
        if ext.isEmpty,let url=respone.url as? URL {
            ext=getExtension(url.lastPathComponent,".");
        }
        return ext.isEmpty ? "tmp":ext;
    }
}
