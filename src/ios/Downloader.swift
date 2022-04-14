

class Downloader {
    static func download(_ url:URL){
        let sessionConfig=NSURLSessionConfiguration.defaultSessionConfiguration();
        let session=NSURLSession(configuration:sessionConfig,delegate:nil,delegateQueue:nil);
        let request=NSMutableURLRequest(URL:url);
        request.HTTPMethod="GET";
        let task=session.dataTaskWithRequest(request,completionHandler:{(data:NSData!,response:NSURLResponse!,error:NSError!)->Void in
            if (error == nil) {
                // Success
                let statusCode=(response as NSHTTPURLResponse).statusCode;
                println("Success: \(statusCode)")

                // This is your file-variable:
                // data
            }
            else {
                // Failure
                println("Failure: %@", error.localizedDescription);
            }
        });
        task.resume();
    }
}
