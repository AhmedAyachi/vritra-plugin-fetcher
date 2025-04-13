declare const Fetcher:Fetcher;


interface Fetcher {
    /**
    * @android
    * System may not be able to open
    * downloaded file in simultor   
    */
    download(options:{
        url:string,
        /**
        * The location in which should save the file
        * @default
        * Android: context.getExternalCacheDir()
        * iOS: FileManager.SearchPathDirectory.cachesDirectory
        * @android
        * Location should not be on internal storage, only externals
        */
        location?:string,
        /**
        * The new downloaded file base name (without the extension)
        * If a file with this name already exists,
        * the string "_\<random_int>" is used as a suffix
        * default: <the app name>
        */
        withBaseName?:string,
        /**
        * If true and a file with th same name already exists in the location specified
        * then that file is replaced
        * @default false
        */
        overwrite?:boolean,
        /**
        * A string used as a toast message 
        * when the file is downloaded
        */
        toast?:string,
        /**
        * if false, no notification is shown to indicate
        * the download progress.
        * default: true
        */
        notify?:boolean,
        /**
         * If true, the downloaded file is saved to the user's gallery.
         * @default false
         */
        saveToUserGallery?:boolean,
        onProgress(data:FetcherDownloadData):void,
        onFail(error:{
            message:string,
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
        header?:object,
        /**
        * The form-data fields
        */
        body?:object,
        /**
        * A string to use as a toast message when the upload is successful
        */
        toast?:string,
        /**
        * if false, no notification is shown to indicate
        * the download progress.
        * default: true
        */
        notify?:boolean,
        files:FetcherFile[],
        /**
        * If true the upload notification will show the upload progress
        * of each file separately otherwise a single progress for all files
        * @default false
        */
        trackEachFile:boolean,
        onProgress(data:FetcherUploadData):void,
        onFail(error:{
            message:string,
            response?:FetcherResponse,
        }):void,
    }):void,
}

interface FetcherFile {
    path:string,
    /**
     * file key
     * @default `file${index}`
     */
    key?:string,
    /**
    * the file mime type if you wish to specify it otherwise it's determined automatically.
    */
    type?:string,
    /**
     * The uploaded file new base name (without extension).
     * Default to its original name.
     * @notice The string should not include the file extension
     */
    withBaseName?:string,
}

type FetcherDownloadData=FetcherData&{
    /**
     * Downloaded file entry. Only set when the file has been downloaded. 
     */
    entry?:{name:string,fullpath:string},
};

type FetcherUploadData=FetcherData&{
    /**
    * Excluded files that could not be uploaded.
    * Other files will be uploaded normally.
    * A falsy value is used if none.
    * Value only available if isFinished true, so make sure you specify this condition
    * isFinished&&excluded before using the array
    */
    excluded?:FetcherFile[],
    /**
     * Upload/Download notification identifier
     * 
     * Available when the request is finished and notify option is true
     * @notice to be dismised using vritra-plugin-notifier
     */
    notificationId?:number,
};

interface FetcherData {
    /**
    * The total progress percentage.
    * For upload method, the value is not affected by the value of trackEachFile property
    */
    progress:number,
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
    protocol:string,
    code:number,
    /**
    * Internal error message if any
    */
    message:string,
    /**
    * Same value as the url property
    */
    url:string,
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
    body:any|object,
}
