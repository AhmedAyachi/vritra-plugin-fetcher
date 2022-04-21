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
                AF.upload(
                    multipartFormData:{[self] in self.setMultipartFormData(files,$0)},
                    to:url,method:.post,headers:nil
                ).uploadProgress(queue:.main,closure:{[self] progress in
                    self.onUploading(progress);
                }).responseJSON(completionHandler:{[self] feedback in
                    switch(feedback.result){
                        case .success:
                            self.onSuccess(feedback);
                            break;
                        case .failure:
                            self.onError(feedback);
                            break;
                        default:break;
                    }
                    /* print("result:",response.result);
                    print("value:",response.value);
                    print("data:",String(decoding:response.data,as:UTF8.self));
                    print("description:",response.description);
                    print("code:",response.response) */
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
                    fileName:filename==nil ? path.lastPathComponent:"\(filename).\(Fetcher.getExtension(path.lastPathComponent))",
                    mimeType:file["type"] as? String ?? "*/*"
                );
            };
        });
        self.useBody(form);
    }

    private func useBody(_ data:MultipartFormData){
        if let body=props["body"] as? [AnyHashable:Any] {
            for (key,value) in body {
                data.append(value as? Data ?? Data(),withName:"\(key)");
            }
        }
    }

    private func onUploading(_ progress:Progress){
        print("progress:",progress.fractionCompleted);
    }

    private func onSuccess(_ feedback:DataResponse<Any,AFError>){
        let response=Uploader.getFetcherResponse(feedback);
        print("upload succeeded",response);
    }

    private func onError(_ feedback:DataResponse<Any,AFError>){
        let response=Uploader.getFetcherResponse(feedback);
        print("error:",feedback.error);
        print("upload failed",response);
    }

    static func getFetcherResponse(_ feedback:DataResponse<Any,AFError>)->[String:Any]{
        let response=feedback.response;
        var res:[String:Any]=[
            "protocol":false,
            "code":response?.statusCode ?? false,
            "message":false,
            "url":response.url.absoluteString ?? false,
            "isSuccessful":false,
            "body":false,
        ];
        if let data=feedback.data,let json=String(data:data,encoding:.utf8),
            let object=try? JSONSerialization.jsonObject(with:data),
            let dic=object as? [String:Any]{
            res["body"]=dic;
        };
        return res;
    }
}

/* class FetcherResponse:[String:Any] {
    var _protocol:String;
    var code:Number;
    var message:String;
    var url:String;
    var isSuccessful:Boolean;
    var body:String;

    init(_ feedback:DataResponse<Any,AFError>){
      
    }
}  */
