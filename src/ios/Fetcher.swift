import Foundation;


class Fetcher:FetcherPlugin {

    @objc(download:)
    func download(command:CDVInvokedUrlCommand){
        do{
            try self.fetch(command,"download");
        }
        catch{
            self.error(command,["message":error.localizedDescription]);
        }        
    }

    @objc(upload:)
    func upload(command:CDVInvokedUrlCommand){
        do{
            try self.fetch(command,"upload");
        }
        catch{
            self.error(command,["message":error.localizedDescription]);
        } 
    }

    private func fetch(_ command:CDVInvokedUrlCommand,_ mode:String="download")throws{
        if let props=command.arguments[0] as? [AnyHashable:Any],!((props["url"] as? String)==nil){
            Fetcher.askPermissions({[self] granted,data in
                if(granted){
                    self.commandDelegate?.run(inBackground:{[self] in
                        let fetcher:FetcherDelegate=mode=="download" ? Downloader(props):Uploader(props);
                        if let method=mode=="download" ? fetcher.download:fetcher.upload {
                            method({[self] params in 
                                self.onProgress(command,params,props);
                            },{[self] error in
                                self.onFail(command,error);
                            });
                        };
                    });
                }
            });
        }
        else{
            throw Fetcher.Error("url attribute is required");
        }
    }
    
    private func onProgress(_ command:CDVInvokedUrlCommand,_ params:[AnyHashable:Any],_ props:[AnyHashable:Any]){
        let isFinished=params["isFinished"] as? Bool ?? true;
        if isFinished,let toast=props["toast"] as? String {
            self.toast(toast);
        }
        self.success(command,params,NSNumber(value:!isFinished));
    }

    private func onFail(_ command:CDVInvokedUrlCommand,_ error:[AnyHashable:Any]){
        self.error(command,error);
    };
    
    private func toast(_ message:String){
        DispatchQueue.main.async(execute:{
            let alert=UIAlertController(title:"",message:message,preferredStyle:.actionSheet);
            DispatchQueue.main.asyncAfter(deadline:DispatchTime.now()+2){
                alert.dismiss(animated:true);
            }
            self.viewController.present(alert,animated:true);
        });
    }

    static func askPermissions(_ onGranted:@escaping(Bool,Any)->Void){
        let center=UNUserNotificationCenter.current();
        center.requestAuthorization(options:[.alert,.sound,.badge],completionHandler:{granted,error  in
            if(granted){
                center.getNotificationSettings(completionHandler:{ settings in
                    onGranted(granted,settings);
                });
            }
            else{
                onGranted(granted,error ?? false);
            }
        });
    }

    static func getExtension(_ path:String,_ separator:String=".")->String{
        let parts=path.split(separator:separator.first ?? ".");
        var ext=parts.count>1 ? String(parts.last!) :"";
        if(ext.isEmpty){
            ext="tmp";
        }
        return ext;
    }

    static func getExtension(_ respone:URLResponse)->String{
        var ext="";
        if let mimetype=respone.mimeType {
            ext=Fetcher.getExtension(mimetype,"/");

        }
        if ext.isEmpty,let name=respone.suggestedFilename {
            ext=Fetcher.getExtension(name,".");
        }
        if ext.isEmpty,let url=respone.url {
            ext=getExtension(url.lastPathComponent,".");
        }
        return ext.isEmpty ? "tmp":ext;
    }
}

@objc protocol FetcherDelegate {
    @objc optional func download(onProgress:(([AnyHashable:Any])->Void)?,onFail:(([AnyHashable:Any])->Void)?);
    @objc optional func upload(onProgress:(([AnyHashable:Any])->Void)?,onFail:(([AnyHashable:Any])->Void)?);
} 