import Alamofire;


class Uploader:NSObject,FetcherDelegate,UNUserNotificationCenterDelegate{

    private var notificationId:String?;
    private var props:[String:Any]=[:];
    private var files:[[String:Any]]=[];
    private var excluded:[[String:Any]]=[];
    private var onProgress:(([String:Any])->Void)?;
    private var onFail:(([String:Any])->Void)?;
    private lazy var progress:Int=0;
    private lazy var trackedindex:Int=(-1);
    private var totalSize:Int=0;

    init(_ props:[String:Any]){
        super.init();
        self.props=props;
    };

    func upload(onProgress:(([String:Any])->Void)?,onFail:(([String:Any])->Void)?){
        self.onFail=onFail;
        if let url=props["url"] as? String {
            self.setFiles();
            let notify=props["notify"] as? Bool ?? true;
            if(notify){
                self.notificationId="\(Int.random(in:0...99999))";
            }
            if(!self.files.isEmpty){
                self.onProgress=onProgress;
                self.notify();
                AF.upload(
                    multipartFormData:{[self] in self.setMultipartFormData($0)},
                    to:url,
                    method:.post,
                    headers:getHeaders()
                ).uploadProgress(
                    queue:.main,
                    closure:{[self] in self.onUploading($0)
                }).responseJSON(completionHandler:{[self] feedback in
                    self.onResponse(feedback);
                });
            }
            else{
                self.onFail?(["message":"could not upload any file"]);
            }
        }
        else{
            self.onFail?(["message":"invalid url"]);
        }
    }

    private func setFiles(){
        if let files=props["files"] as? [[String:Any]]{
            for i in 0..<files.count {
                var file=files[i];
                if var path=file["path"] as? String {
                    if(path.hasPrefix("file:")){path=path.replacingOccurrences(of:"file:",with:"")};
                    while(path.contains("//")){path=path.replacingOccurrences(of:"//",with:"/")};
                    let url=URL(fileURLWithPath:path);
                    if let data=try? Data(contentsOf:url){
                        let size=(try? url.resourceValues(forKeys:[URLResourceKey.fileSizeKey]).fileSize ?? 1) ?? 1;
                        file["size"]=size;
                        file["data"]=data;
                        file["name"]=Uploader.getFileName(file);
                        self.totalSize+=size;
                        self.files.append(file);
                    }
                    else{
                        self.excluded.append(file);
                    }
                }
                else{
                    self.excluded.append(file);
                }
            }
        }
    }

    private func getHeaders()->HTTPHeaders{
        var headers:HTTPHeaders=[:];
        if let header=props["header"] as? [String:Any] {
            for (key,value) in header {
                headers["\(key)"]="\(value)";
            }
        }
        return headers;
    }
    
    private func setMultipartFormData(_ form:MultipartFormData){
        for i in 0..<files.count {
            let file=files[i];
            let key=file["key"] as? String ?? "file\(i)";
            form.append(
                file["data"] as! Data,
                withName:key,
                fileName:Uploader.getFileName(file),
                mimeType:file["type"] as? String ?? "*/*"
            );
        }
        self.setFormDataBody(form);
    }

    private func setFormDataBody(_ data:MultipartFormData){
        if let body=props["body"] as? [String:Any] {
            for (key,value) in body {
                data.append("\(value)".data(using:.utf8) ?? Data(),withName:"\(key)");
            }
        }
    }

    private func onUploading(_ progress:Progress){
        let value=Int(progress.fractionCompleted*100);
        let uploadedSize=self.totalSize*value/100;
        var i:Int = -1,accumulater:Int=0;
        repeat {
            i+=1;
            accumulater+=files[i]["size"] as! Int;
        } while(accumulater<uploadedSize);
        self.trackedindex=i;
        self.progress=value;
        self.notify();
        self.onProgress?([
            "progress":value,
            "isFinished":false,
        ]);
    }

    private func onResponse(_ feedback:DataResponse<Any,AFError>){
        let response=feedback.response;
        let code=response?.statusCode ?? -1;
        if((200...299).contains(code)){
            self.progress=100;
            self.onProgress?([
                "progress":100,
                "isFinished":true,
                "notificationId":self.notificationId != nil ? Int(self.notificationId!) : nil,
                "response":Fetcher.getResponse(feedback),
                "excluded":self.excluded.count>0 ? self.excluded:false,
            ]);
        }
        else if let notifId=self.notificationId {
            let center=UNUserNotificationCenter.current();
            center.removeDeliveredNotifications(withIdentifiers:[notifId]);
            self.onFail?([
                "message":feedback.error?.localizedDescription ?? "Unknown error",
                "response":Fetcher.getResponse(feedback),
            ]);
        }
    }

    private func notify(){if let notifId=self.notificationId {
        let length=self.files.count;
        let content=UNMutableNotificationContent();
        content.title=Fetcher.appname;
        if(progress>=100){
            content.body="\(length>1 ? "\(length) files" : "file") uploaded successfully";
        }
        else if(trackedindex<0){
            content.body="Uploading \(length>1 ? "\(length+excluded.count) files" : self.files[0]["name"]!)";
        }
        else{
            let file=files[trackedindex];
            let trackEachFile=props["trackEachFile"] as? Bool ?? false;
            if(trackEachFile&&(length>1)){
                let size=file["size"] as! Int;
                var overflow=0;
                for i in 0..<trackedindex {
                    overflow+=self.files[i]["size"] as! Int;
                }
                let downloaded=progress*totalSize/100;
                content.subtitle="\((downloaded-overflow)*100/size)%";
            }
            else{
                content.subtitle="\(progress)%";
            }
            content.body="Uploading \(file["name"] ?? "file")";
        }
        let request=UNNotificationRequest(
            identifier:notifId,
            content:content,
            trigger:nil
        );
        let center=UNUserNotificationCenter.current();
        center.delegate=self;
        center.add(request,withCompletionHandler:{[self] error in
            if(error != nil){
                self.onFail?(["message":error!.localizedDescription]);
            }
        });
    }}

    func userNotificationCenter(_ center:UNUserNotificationCenter,willPresent notification:UNNotification,withCompletionHandler completionHandler:@escaping(UNNotificationPresentationOptions)->Void){
        center.getDeliveredNotifications{[self] notifications in
            if(!notifications.contains(where:{notification in notification.request.identifier==self.notificationId})){
                completionHandler([.alert,.badge,.sound]);
            }
        };
    }

    static func getFileName(_ file:[String:Any],_ original:Bool=false)->String{
        var filename:String?;
        let path=file["path"] as? String ?? "";
        if(!path.isEmpty){
            var basename:String?=original ? nil : file["withBaseName"] as? String;
            let url=URL(fileURLWithPath:path);
            filename=basename==nil ? url.lastPathComponent : "\(basename!).\(Fetcher.getExtension(url.lastPathComponent))";
        };
        return filename ?? "";
    }
}
