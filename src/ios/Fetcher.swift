import Foundation;


class Fetcher:FetcherPlugin {

    @objc(download:)
    func download(command:CDVInvokedUrlCommand){
        let argument=command.arguments[0] as? [AnyHashable:Any];
        if !(argument==nil){
            do{
                let params=argument!;
                let link=params["url"] as? String ?? "";
                if !(link==nil){
                    let url=URL(string:link!);
                    Downloader.download(url);
                }
            }
            catch{
                self.error(command,error.localizedDescription);
            }
        }
    }
}