

class Downloader:NSObject,FetcherDelegate,URLSessionDelegate,URLSessionDownloadDelegate,UNUserNotificationCenterDelegate{
    
    static let appname=Bundle.main.infoDictionary?["CFBundleDisplayName" as String] as? String ?? "";
    var props:[AnyHashable:Any]=[:];
    var location:URL?;
    var filename:String="";
    var onProgress:(([AnyHashable:Any])->Void)?=nil;
    var onFail:(([AnyHashable:Any])->Void)?=nil;

    init(_ props:[AnyHashable:Any]){
        super.init();
        self.props=props;
        setLocation();
    }

    func download(onProgress:(([AnyHashable:Any])->Void)?,onFail:(([AnyHashable:Any])->Void)?){
        if let link=props["url"] as? String,let url=URL(string:link) {
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
        else{
            self.onFail?(["message":"invalid url"]);
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
            let notify=props["notify"] as? Bool ?? true;
            if(notify){
                self.notify();
            }
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
        if let error=didBecomeInvalidWithError{
            onFail?(["message":error.localizedDescription]);
        }
    }
    
    private func saveFile(_ path:URL,_ task:URLSessionDownloadTask,_ session:URLSession)throws{
        if let location=self.location,let response=task.response {
            let filemanager=FileManager.default;
            let basename:String=props["filename"] as? String ?? Downloader.appname;
            let ext:String=Downloader.getExtension(response);
            filename="\(basename).\(ext)";
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
            throw Fetcher.Error("\(self.location==nil ?"location":"Request Response") is undefined");
        }
    }

    private func notify(){
        let content=UNMutableNotificationContent();
        content.title=Downloader.appname;
        content.subtitle=filename;
        content.body="Download complete.";
        let request=UNNotificationRequest(
            identifier:"fetcherdownload",
            content:content,
            trigger:nil
        );
        let center=UNUserNotificationCenter.current();
        center.delegate=self;
        center.add(request,withCompletionHandler:{[self] error in
            if !(error==nil){
                onFail?(["message":error!.localizedDescription]);
            }
        });
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter,willPresent notification: UNNotification,withCompletionHandler completionHandler: (UNNotificationPresentationOptions)->Void){
        completionHandler([.alert,.badge,.sound]);
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
