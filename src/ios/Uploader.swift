

class Uploader:NSObject,FetcherDelegate,URLSessionDelegate,URLSessionDataDelegate{

    private let boundary:String=UUID().uuidString;
    var props:[AnyHashable:Any]=[:];
    var onProgress:(([AnyHashable:Any])->Void)?;
    var onFail:(([AnyHashable:Any])->Void)?;

    init(_ props:[AnyHashable:Any]){
        super.init();
        self.props=props;
    };

    func upload(onProgress:(([AnyHashable:Any])->Void)?,onFail:(([AnyHashable:Any])->Void)?){
        if let link=props["url"] as? String,let url=URL(string:link){
            print("upload called");
            if let files=props["files"] as? [String] {
                let sessionConfig=URLSessionConfiguration.default;
                let session=URLSession(
                    configuration:sessionConfig,
                    delegate:self,
                    delegateQueue:nil
                );
                var request=URLRequest(url:url);
                request.httpMethod="POST";
                request.allHTTPHeaderFields=[
                    "Content-Type":"multipart/form-data; boundary=\(boundary)",
                    //"Accept":"application/json",
                ];
                request.httpBody=getHttpBody();
                self.onProgress=onProgress;
                self.onFail=onFail;
                files.forEach({path in
                    let task=session.uploadTask(
                        with:request,
                        from:"text=text content".data(using:.utf8),//URL(fileURLWithPath:path),
                        completionHandler:{data,response,error in
                            print("data:",String(data:data ?? Data(),encoding:.utf8));
                            print("response:",response ?? "nil");
                            print("error:",error ?? "nil");
                        }
                    );
                    task.resume();
                });
            }
        }
        else{
            self.onFail?(["message":"invalid url"]);
        }
    }
    
    private func getHttpBody()->Data{
        let name="file",value="Text.txt";
        var body=Data();
        body.append("--\(boundary)\r\n");
        body.append("Content-Disposition: form-data; name=\"\(name)\"\r\n");
        body.append("Content-Type: text/plain; charset=ISO-8859-1\r\n");
        //body.append("Content-Transfer-Encoding: 8bit\r\n");
        body.append("\r\n");
        body.append("\(value)\r\n");
        body.append("--\(boundary)--\r\n");
        return body;
    }

    /* func urlSession(_ session:URLSession,didBecomeInvalidWithError:Error?){
        print("didBecomeInvalidWithError:",didBecomeInvalidWithError ?? "nil");
    }

    func urlSession(_ session:URLSession,dataTask task:URLSessionDataTask,didReceive response:URLResponse,completionHandler:(URLSession.ResponseDisposition)->Void) {
       print("response:",response);
       
   } */

}

fileprivate extension Data{
    mutating func append(_ string: String){
        if let data=string.data(using:.utf8){
            append(data);
        }
   }
}
