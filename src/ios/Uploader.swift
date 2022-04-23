import Alamofire;


class Uploader:NSObject,FetcherDelegate,UNUserNotificationCenterDelegate{

    private let id:String="fetcher\(Int.random(in:0...99999))";
    private var files:[[AnyHashable:Any]]=[];
    private var props:[AnyHashable:Any]=[:];
    private var onProgress:(([AnyHashable:Any])->Void)?;
    private var onFail:(([AnyHashable:Any])->Void)?;
    private lazy var progress:Int=0;
    private lazy var trackedindex:Int=(-1); 
    private var trackedfile:[AnyHashable:Any]?;
    private var totalSize:Int=0;
    private lazy var unit=100/files.count;

    init(_ props:[AnyHashable:Any]){
        super.init();
        self.props=props;
    };

    func upload(onProgress:(([AnyHashable:Any])->Void)?,onFail:(([AnyHashable:Any])->Void)?){
        if let url=props["url"] as? String {
            if let files=props["files"] as? [[AnyHashable:Any]]{
                self.files=files;
                self.onProgress=onProgress;
                self.onFail=onFail;
                self.notify();
                AF.upload(
                    multipartFormData:{[self] in self.setMultipartFormData($0)},
                    to:url,method:.post,headers:nil
                )
                .uploadProgress(queue:.main,closure:{[self] in self.onUploading($0)})
                .responseJSON(completionHandler:{[self] feedback in
                    switch(feedback.result){
                        case .success:self.onSuccess(feedback);break;
                        case .failure:self.onError(feedback);break;
                    }
                });
            }
        }
        else{
            self.onFail?(["message":"invalid url"]);
        }
    }
    
    private func setMultipartFormData(_ form:MultipartFormData){
        let newFileNameKey=props["newFileNameKey"] as? String ?? "filename";
        for var file in files{
            if let path=file["path"] as? String {
                let url=URL(fileURLWithPath:path);
                if let data=try? Data(contentsOf:url){
                    let filename=Uploader.getFileName(file);
                    file["name"]=filename;
                    let size=(try? url.resourceValues(forKeys:[URLResourceKey.fileSizeKey]).fileSize ?? 1) ?? 1;
                    self.totalSize+=size;
                    file["size"]=size;
                    form.append(
                        data,
                        withName:newFileNameKey,
                        fileName:filename,
                        mimeType:file["type"] as? String ?? "*/*"
                    );
                };   
            }
        }
        self.useBody(form);
    }

    private func useBody(_ data:MultipartFormData){
        if let body=props["body"] as? [String:Any] {
            for (key,value) in body {
                data.append("\(value)".data(using:.utf8) ?? Data(),withName:"\(key)");
            }
        }
    }

    private func onUploading(_ progress:Progress){
        var value=Int(progress.fractionCompleted*100);
        if(value>=100){
            value=99;
        }
        let length=files.count,index=Int(value/(100/length));
        if(index<length){
            self.trackedindex=index;
            self.trackedfile=files[index];
            self.progress=value;
            self.notify();
        }
        self.onProgress?([
            "progress":value,
            "isFinished":false,
            "response":false,
        ]);
    }

    private func onSuccess(_ feedback:DataResponse<Any,AFError>){
        let response=feedback.response;
        let code=response?.statusCode ?? -1;
        self.progress=100;
        self.notify();
        if((200...299).contains(code)){
            self.onProgress?([
                "progress":100,
                "isFinished":true,
                "response":Fetcher.getResponse(feedback),
            ]);
        }
        else{
            self.onError(feedback);
        }
    }

    private func notify(){
        let length=files.count;
        let content=UNMutableNotificationContent();
        content.title=Fetcher.appname;
        if(progress>=100){
            content.body="\(length>1 ? "\(length) files":"file") uploaded successfully";
        }
        else if(trackedindex<0){
            content.body="Uploading \(length>1 ?"\(length) files":Uploader.getFileName(files[0]))";
        }
        else{
            let file=files[trackedindex];
            let trackEachFile=props["trackEachFile"] as? Bool ?? false;
            if(trackEachFile&&(length>1)){
                let overflow=trackedindex<1 ? 0 :file["size"] as? Int ?? 0;
                let downloaded=progress*totalSize/100;
                content.subtitle="\((downloaded-overflow)*100/totalSize)%";
                print(file["name"] ?? "",(downloaded-overflow)*100/totalSize);
                //(progress-trackedindex*unit)*100/unit
            }
            else{
                content.subtitle="\(progress)%";
            }
            content.body="Uploading \(file["name"] ?? "file")";
        }
        let request=UNNotificationRequest(
            identifier:self.id,
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

    func userNotificationCenter(_ center:UNUserNotificationCenter,willPresent notification:UNNotification,withCompletionHandler completionHandler:@escaping(UNNotificationPresentationOptions)->Void){
        center.getDeliveredNotifications{[self] notifications in 
            if(!notifications.contains(where:{notification in notification.request.identifier==self.id})){
                completionHandler([.alert,.badge,.sound]);   
            }
        };
    }

    func userNotificationCenter(_ center:UNUserNotificationCenter,didReceive response:UNNotificationResponse,withCompletionHandler:()->Void){
        //print("notification \(response.notification.request.identifier)dismissed");
    }

    private func onError(_ feedback:DataResponse<Any,AFError>){
        let center=UNUserNotificationCenter.current();
        center.removeDeliveredNotifications(withIdentifiers:[self.id]);
        self.onFail?([
            "message":feedback.error?.localizedDescription ?? "Unknown error",
            "response":Fetcher.getResponse(feedback),
        ]);
    }

    static func getFileName(_ file:[AnyHashable:Any])->String{
        var filename:String?;
        let path=file["path"] as? String ?? "";
        if(!path.isEmpty){
            let url=URL(fileURLWithPath:path)
            filename=file["newName"] as? String;
            filename=filename==nil ? url.lastPathComponent:"\(filename!).\(Fetcher.getExtension(url.lastPathComponent))";
        };
        return filename ?? "";
    }
}
