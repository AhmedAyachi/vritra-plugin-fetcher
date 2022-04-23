import Foundation;
import Alamofire;


class Fetcher:FetcherPlugin {

    static let appname=Bundle.main.infoDictionary?["CFBundleDisplayName" as String] as? String ?? "";

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
        let isFinished=params["isFinished"] as? Bool ?? false;
        if isFinished,let message=props["toast"] as? String {
            self.toast(message);
        }
        self.success(command,params,NSNumber(value:!isFinished));
    }

    private func onFail(_ command:CDVInvokedUrlCommand,_ error:[AnyHashable:Any]){
        self.error(command,error);
    };
    
    private func toast(_ message:String){
        DispatchQueue.main.async(execute:{[self] in
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

    static func getResponse(_ feedback:DataResponse<Any,AFError>)->[String:Any]{
        let response=feedback.response;
        let code=response?.statusCode ?? -1;
        var res:[String:Any]=[
            "protocol":"",
            "code":code<0 ? false:code,
            "message":"",
            "url":response?.url?.absoluteString ?? false,
            "isSuccessful":(200...299).contains(code),
            "body":false,
        ];
        if let data=feedback.data,let json=try? JSONSerialization.jsonObject(with:data),
            let body=json as? [String:Any]{
            res["body"]=body;
        };
        return res;
    }

    /* static func getResponse(_ error:Error)->[String:Any]{
        var response:[String:Any]=[
            "protocol":false,
            "code":false,
            "message":"",
            "url":response?.url?.absoluteString ?? false,
            "isSuccessful":(200...299).contains(code),
            "body":false,
        ];
        return response;
    } */
}

@objc protocol FetcherDelegate {
    @objc optional func download(onProgress:(([AnyHashable:Any])->Void)?,onFail:(([AnyHashable:Any])->Void)?);
    @objc optional func upload(onProgress:(([AnyHashable:Any])->Void)?,onFail:(([AnyHashable:Any])->Void)?);
} 