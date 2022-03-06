declare const Fetcher:Fetcher;


interface Fetcher{
    download(params:{
        url:String
        location?:String,
        filename?:String,
        type?:String,
        toast?:String,
        overwrite?:Boolean,
        notify?:Boolean,
        onProgress(info:{
            progress:Number,
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
