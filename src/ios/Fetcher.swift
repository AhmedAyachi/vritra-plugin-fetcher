import Foundation;


class Fetcher:FetcherPlugin {

    @objc(download:)
    func download(command:CDVInvokedUrlCommand){
        if let props=command.arguments[0] as? [AnyHashable:Any]{
            do{
                if let link=props["url"] as? String {
                    let downloader=Downloader(props);
                    downloader.download(
                        onProgress:{[self] params in 
                            let isFinished=params["isFinished"] as? Bool ?? true;
                            if isFinished,let toast=props["toast"] as? String {
                                self.toast(toast);
                            }
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

    func toast(_ message:String){
        let alert=UIAlertController(title:"",message:message,preferredStyle:.actionSheet);
        DispatchQueue.main.asyncAfter(deadline:DispatchTime.now()+2){
            alert.dismiss(animated:true);
        }
        DispatchQueue.main.async(execute:{
            self.viewController.present(alert,animated:true);
        });
    }
}