import Foundation;


class Fetcher:FetcherPlugin {

    @objc(download:)
    func download(command:CDVInvokedUrlCommand){
        if let params=command.arguments[0] as? [AnyHashable:Any]{
            do{
                if let link=params["url"] as? String{
                    let downloader=Downloader(params);
                    downloader.download(
                        onProgress:{[self] params in 
                            let isFinished=params["isFinished"] as? Bool ?? true;
                            self.success(command,params,NSNumber(value:!isFinished));
                        },
                        onFail:{[self] message in
                            error(command,message);
                        }
                    );
                }
                else{
                    throw Fetcher.Error("url attribute is required");
                }
            }
            catch{
                self.error(command,error.localizedDescription);
            }
        };
    }
}