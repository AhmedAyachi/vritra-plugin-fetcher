import Alamofire;


class Uploader:NSObject,FetcherDelegate{

    private let boundary:String=UUID().uuidString;
    var props:[AnyHashable:Any]=[:];
    var onProgress:(([AnyHashable:Any])->Void)?;
    var onFail:(([AnyHashable:Any])->Void)?;

    init(_ props:[AnyHashable:Any]){
        super.init();
        self.props=props;
    };

    func upload(onProgress:(([AnyHashable:Any])->Void)?,onFail:(([AnyHashable:Any])->Void)?){
        if let url=props["url"] as? String {
            if let files=props["files"] as? [[AnyHashable:Any]]{
                self.onProgress=onProgress;
                self.onFail=onFail;
                AF.upload(
                    multipartFormData:{[self] in self.setMultipartFormData(files,$0)},
                    to:url,method:.post,headers:nil
                ).uploadProgress(queue:.main,closure:{[self] progress in
                    self.onUploading(progress);
                }).responseJSON(completionHandler:{[self] feedback in
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
    
    private func setMultipartFormData(_ files:[[AnyHashable:Any]],_ form:MultipartFormData){
        let newFileNameKey=props["newFileNameKey"] as? String ?? "filename";
        files.forEach({file in
            let path=URL(fileURLWithPath:file["path"] as! String);
            let filename=file["newName"] as? String;
            if let data=try? Data(contentsOf:path){
                form.append(
                    data,
                    withName:newFileNameKey,
                    fileName:filename==nil ? path.lastPathComponent:"\(filename!).\(Fetcher.getExtension(path.lastPathComponent))",
                    mimeType:file["type"] as? String ?? "*/*"
                );
            };
        });
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
        self.onProgress?([
            "progress":Int(progress.fractionCompleted*100),
            "isFinished":false,
            "response":false,
        ]);
    }

    private func onSuccess(_ feedback:DataResponse<Any,AFError>){
        let response=feedback.response;
        let code=response?.statusCode ?? -1;
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

    private func onError(_ feedback:DataResponse<Any,AFError>){
        self.onFail?([
            "message":feedback.error?.localizedDescription ?? "Unknown error",
            "response":Fetcher.getResponse(feedback),
        ]);
    }
}
