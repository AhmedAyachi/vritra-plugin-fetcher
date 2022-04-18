import Foundation;


class Fetcher:FetcherPlugin {

    @objc(download:)
    func download(command:CDVInvokedUrlCommand){
        if let props=command.arguments[0] as? [AnyHashable:Any]{
            self.commandDelegate?.run(inBackground:{[self] in
                do{
                    if !((props["url"] as? String)==nil){
                        let downloader=Downloader(props);
                        downloader.download(
                            onProgress:{[self] params in self.onDownloading(command,params,props)},
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
            });
        };
    }

    private func toast(_ message:String){
        DispatchQueue.main.async(execute:{
            let alert=UIAlertController(title:"",message:message,preferredStyle:.actionSheet);
            DispatchQueue.main.asyncAfter(deadline:DispatchTime.now()+2){
                alert.dismiss(animated:true);
            }
            self.viewController.present(alert,animated:true);
        });
    }

    private func onDownloading(_ command:CDVInvokedUrlCommand,_ params:[AnyHashable:Any],_ props:[AnyHashable:Any]){
        let isFinished=params["isFinished"] as? Bool ?? true;
        if isFinished,let toast=props["toast"] as? String {
            self.toast(toast);
        }
        self.success(command,params,NSNumber(value:!isFinished));
    }
}