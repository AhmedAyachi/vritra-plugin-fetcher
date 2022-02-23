declare const Fetcher:Fetcher;


interface Fetcher{
    download(params:{
        url:String
        location?:String,
        filename?:String,
        type?:String,
        toast?:String,
        onProgress(info:{
            progress:Number,
            isFinished:Boolean,
        }):void,
        onFail(message:String):void,
    }):void;
    upload(params:{
        url:string,
        body?:Object,
        toast?:String,
        encoding:"form-data",
        files:{
            path:String,
            type?:String,
            formData?:{key:String},
        }[],
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
