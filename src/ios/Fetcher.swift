import Foundation;


class Fetcher:FetcherPlugin {

    @objc(download:)
    func download(command:CDVInvokedUrlCommand){
        if let params=command.arguments[0] as? [AnyHashable:Any]{
            do{
                if let link=params["url"] as? String{
                    if let url=URL(string:link){
                        let downloader=Downloader();
                        downloader.download(url);
                    };
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