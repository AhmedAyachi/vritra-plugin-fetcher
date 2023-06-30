declare const Fetcher:Fetcher;


interface Fetcher{
    /**
    * @Android
    * System may not be able to open
    * downloaded file in simultor   
    */
    download(options:{
        url:String
        /**
        * The location in which should save the file
        * @default
        * ios: Documents folder
        * android: Download folder
        * @Android
        * Location should not be on internal storage, only externals
        */
        location?:String,
        /**
        * The new downloaded file name without the extension
        * If a file with this name already exists:
        * the string " (<random-int>)" is used as a suffix
        * default: <the app name>
        */
        filename?:String,
        /**
        * the file mime type
        * required in some requests
        */
        type?:String,
        /**
        * A string used as a toast message 
        * when the file is downloaded
        */
        toast?:String,
        /**
        * If true and a file already exists in the location specified
        * with the given filename property, that file will be replaced
        * default: false
        */
        overwrite?:Boolean,
        /**
        * if false, no notification is shown to indicate
        * the download progress.
        * default: true
        */
        notify?:Boolean,
        onProgress(data:FetcherProgressData):void,
        onFail(error:{
            message:String,
        }):void,
    }):void;
    upload(options:{
        /**
        * the target upload url
        */
        url:string,
        encoding:"form-data",
        /**
        * Headers object
        */
        header?:Object,
        /**
        * The form-data fields
        */
        body?:Object,
        /**
        * A string to use as a toast message when the upload is successful
        */
        toast?:String,
        /**
        * if false, no notification is shown to indicate
        * the download progress.
        * default: true
        */
        notify?:Boolean,
        files:FetcherFile[],
        /**
        * If true the upload notification will show the upload progress
        * of each file separately otherwise a single progress for all files
        * @default false
        */
        trackEachFile:Boolean,
        onProgress(data:FetcherProgressData&{
            /**
            * Excluded files that could not be uploaded.
            * Other files will be uploaded normally.
            * A falsy value is used if none.
            * Value only available if isFinished true, so make sure you specify this condition
            * isFinished&&excluded before using the array
            */
            excluded?:FetcherFile[],
        }):void,
        onFail(error:{
            message:String,
            response?:FetcherResponse,
        }):void,
    }):void,
}

interface FetcherFile {
    path:String,
    /**
     * file key
     * @default `file${index}`
     */
    key?:String,
    /**
    * the file mime type if you wish to specify it otherwise it's determined automatically
    * @default auto
    */
    type?:String,
    /**
     * The uploaded file new name.
     * The string should not include the file extension
     * @default The file original name
     */
    name?:String,
}

interface FetcherProgressData {
    /**
    * The total progress percentage.
    * For upload method, the value is not affected by the value of trackEachFile property
    */
    progress:Number,
    /**
    * A boolean value that indicates that the server finished dealing with this request.
    * Please do not used this property to verify that the upload request was successful.
    * Use response?.isSuccessful instead
    */
    isFinished:Boolean,
    response?:FetcherResponse,
}

interface FetcherResponse {
    /**
    * always an empty string on ios
    */
    protocol:String,
    code:Number,
    /**
    * Internal error message if any
    */
    message:String,
    /**
    * Same value as the url property
    */
    url:String,
    /**
    * Used to verify if the request was
    * actually successful. Sometimes the onProgress is called,
    * isFinished is true but the request is not really successful
    * because the server rejected the request
    */
    isSuccessful:Boolean,
    /**
    * Server actual response
    * A falsy value is passed if there is none
    */
    body:any|Object,
}
