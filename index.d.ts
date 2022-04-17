declare const Fetcher:Fetcher;


interface Fetcher{
    download(params:{
        url:String
        location?:String,//default: android=>Download folder, ios=>Documents folder
        filename?:String,//without extension
        type?:String,//spceify filename type,required in some requests
        toast?:String,//on finish toast message
        overwrite?:Boolean,//false
        notify?:Boolean,//true
        onProgress(info:{
            progress:Number,//0...100
            isFinished:Boolean,
        }):void,
        onFail(message:String):void,
    }):void;
    upload(params:{
        url:string,
        encoding:"form-data",
        body?:Object,
        newFileNameKey?:String,
        toast?:String,
        files:{
            path:String,
            type?:String,
            newName?:String,
        }[],
        trackEachFile:Boolean,
        onProgress(info:{
            progress:Number,
            isFinished:Boolean,
            response?:_Response,
        }):void,
        onFail(error:{
            message:String,
            response?:_Response,
        }):void,
    }):void,
}

interface _Response{
    protocol:String,
    code:Number,
    message:String,
    url:String,
    isSuccessful:Boolean,
    body:String|Object,
}
