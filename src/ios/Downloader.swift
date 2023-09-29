

class Downloader:NSObject,FetcherDelegate,URLSessionDelegate,URLSessionDownloadDelegate,UNUserNotificationCenterDelegate{
    
    var props:[String:Any]=[:];
    var location:URL?;
    private var notificationId:String?;
    var progress:Int=0;
    var file:[String:Any]=[:];
    var onProgress:(([String:Any])->Void)?=nil;
    var onFail:(([String:Any])->Void)?=nil;

    init(_ props:[String:Any]){
        super.init();
        self.props=props;
    }

    func download(onProgress:(([String:Any])->Void)?,onFail:(([String:Any])->Void)?){
        if let link=props["url"] as? String,let url=URL(string:link) {
            self.setLocation();
            self.setFile(link);
            let notify=props["notify"] as? Bool ?? true;
            if(notify){
                self.notificationId="\(Int.random(in:0...99999))";
            }
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

    private func setFile(_ link:String){
        let ext:String=Fetcher.getExtension(link);
        file["ext"]=ext;
        if let basename=props["withBaseName"] as? String,!basename.isEmpty {
            file["basename"]=basename;
            file["name"]=basename+"."+ext;
        }
        else if let url=URL(string:link) {
            let filename=url.lastPathComponent;
            file["name"]=filename;
            file["basename"]=filename.split(separator:".")[0] ?? Fetcher.appname;
        }
    }

    private func setLocation(){
        if let location=props["location"] as? String {
            self.location=URL(fileURLWithPath:location);
        }
        else{
            self.location=try? FileManager.default.url(
                for:.cachesDirectory,
                in:.userDomainMask,
                appropriateFor:nil,
                create:false
            )
        }
    }
    
    func urlSession(_ session:URLSession,downloadTask:URLSessionDownloadTask,didWriteData:Int64,totalBytesWritten:Int64,totalBytesExpectedToWrite:Int64){
        self.progress=Int(100*(Double(totalBytesWritten)/Double(totalBytesExpectedToWrite)));
        self.notify();
        if let onProgress=self.onProgress {
            let params:[String:Any]=[
                "isFinished":false,
                "progress":self.progress,
            ];
            onProgress(params);
        }
    }

    func urlSession(_ session:URLSession,downloadTask task:URLSessionDownloadTask,didFinishDownloadingTo location:URL){
        do{
            try self.saveFile(location,task,session);
            self.notify();
            if(onProgress != nil){
                let params:[String:Any]=[
                    "isFinished":true,
                    "progress":100,
                    "notificationId":self.notificationId != nil ? Int(self.notificationId!) : nil,
                    "entry":[
                        "name":file["name"],
                        "fullpath":file["fullpath"],
                    ],
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
    
    private func saveFile(_ path:URL,_ task:URLSessionDownloadTask,_ session:URLSession) throws {
        if let location=self.location as? URL {
            let filemanager=FileManager.default;
            var destination=location.appendingPathComponent(file["name"] as! String);
            if(filemanager.fileExists(atPath:destination.path)){
                let overwrite=props["overwrite"] as? Bool ?? false;
                if(overwrite){
                   try filemanager.removeItem(at:destination);
                }
                else{
                    let filename="\(file["basename"]!)_\(Int.random(in:0...99999)).\(file["ext"]!)";
                    file["name"]=filename;
                    destination=location.appendingPathComponent(filename);
                }
            }
            let saveToUserGallery=props["saveToUserGallery"] as? Bool ?? false;
            if saveToUserGallery,
                let data=try? Data(contentsOf:path),
                let image=UIImage(data:data) {
                UIImageWriteToSavedPhotosAlbum(image,nil,nil,nil);
            }
            try filemanager.moveItem(at:path,to:destination);
            file["fullpath"]=destination.absoluteString;
        }
        else{
            throw Fetcher.Error("\(self.location==nil ?"location":"Request Response") is undefined");
        }
    }

    private func notify(){if let notifId=self.notificationId {
        let content=UNMutableNotificationContent();
        content.title=Fetcher.appname;
        content.subtitle="\(self.progress)%";
        let filename=file["name"]!;
        content.body=self.progress>=100 ? "\(filename) downloaded" : "Downloading \(filename)";
        let request=UNNotificationRequest(
            identifier:notifId,
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
    }};

    func userNotificationCenter(_ center: UNUserNotificationCenter,willPresent notification: UNNotification,withCompletionHandler completionHandler: (UNNotificationPresentationOptions)->Void){
        completionHandler([.alert,.badge,.sound]);
    }
}
