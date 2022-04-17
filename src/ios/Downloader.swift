

class Downloader:NSObject,URLSessionDelegate,URLSessionDownloadDelegate{
    
    static let appname=Bundle.main.infoDictionary?["CFBundleDisplayName" as String] as? String ?? "";
    var props:[AnyHashable:Any]=[:];
    var location:URL?;
    var filename:String="";
    var onProgress:(([AnyHashable:Any])->Void)?=nil;
    var onFail:((String)->Void)?=nil;

    init(_ props:[AnyHashable:Any]){
        super.init();
        self.props=props;
        setLocation();
    }

    func download(onProgress:(([AnyHashable:Any])->Void)?=nil,onFail:((String)->Void)?=nil){
        let link=props["url"] as! String;
        if let url=URL(string:link) {
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
        if let path=props["location"] as? String {
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
                "progress":100*(Double(totalBytesWritten)/Double(totalBytesExpectedToWrite)),
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
                    "progress":100,
                ];
                onProgress!(params);
            }
        }
        catch{
            self.urlSession(session,didBecomeInvalidWithError:error);
        }
    }

    func urlSession(_ session:URLSession,didBecomeInvalidWithError:Error?){
        if let error=didBecomeInvalidWithError,!(onFail==nil){
            onFail!(error.localizedDescription);
        }
    }
    
    private func saveFile(_ path:URL,_ task:URLSessionDownloadTask,_ session:URLSession)throws{
        if let location=self.location,let response=task.response {
            let filemanager=FileManager.default;
            let basename:String=props["filename"] as? String ?? Downloader.appname;
            let ext:String=Downloader.getExtension(response);
            var filename="\(basename).\(ext)";
            var destination=location.appendingPathComponent(filename);
            if(filemanager.fileExists(atPath:destination.path)){
                let overwrite=props["overwrite"] as? Bool ?? false;
                if(overwrite){
                   try filemanager.removeItem(at:destination);
                }
                else{
                    filename="\(basename) (\(Int.random(in:0...99999))).\(ext)";
                    destination=location.appendingPathComponent(filename);
                }
            }
            try filemanager.moveItem(at:path,to:destination);
        }
        else{
            throw Fetcher.Error("Request Response is undefined");
        }
    }

    private func notify(){

    }

    static func getURLExtension(_ url:URL)->String{
        let name:String=url.lastPathComponent;
        return Downloader.getExtension(name);
    }

    static func getExtension(_ path:String,_ separator:String=".")->String{
        let parts=path.split(separator:separator.first ?? ".");
        var ext=parts.count>1 ? String(parts.last!) :"";
        if(ext.isEmpty){
            ext="tmp";
        }
        return ext;
    }

    static func getExtension(_ respone:URLResponse)->String{
        var ext="";
        if let mimetype=respone.mimeType {
            ext=Downloader.getExtension(mimetype,"/");

        }
        if ext.isEmpty,let name=respone.suggestedFilename {
            ext=Downloader.getExtension(name,".");
        }
        if ext.isEmpty,let url=respone.url {
            ext=getExtension(url.lastPathComponent,".");
        }
        return ext.isEmpty ? "tmp":ext;
    }
}
