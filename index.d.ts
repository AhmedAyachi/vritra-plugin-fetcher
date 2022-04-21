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
        onFail(error:{
            message:String,
        }):void,
    }):void;
    upload(params:{
        url:string,
        encoding:"form-data",
        body?:Object,
        newFileNameKey?:String,//default: filename
        toast?:String,
        files:{
            path:String,
            type?:String,// file mime type
            newName?:String,//without extension
        }[],
        /**
         *If true the upload notification will show the upload progress
         *of each file separately otherwise a single progress for the sum of the
         *files in progress upload
         *default: false
         *@type {Boolean}
        */
        trackEachFile:Boolean,
        onProgress(info:{
            progress:Number,//always the total progress
            isFinished:Boolean,
            response?:FetcherResponse,
        }):void,
        onFail(error:{
            message:String,
            response?:FetcherResponse,
        }):void,
    }):void,
}

interface FetcherResponse{
    protocol:String,
    code:Number,
    message:String,
    url:String,
    isSuccessful:Boolean,
    /**
     * Server actual response
     */
    body:any|Object,
}
