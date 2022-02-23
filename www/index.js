const exec=require("cordova/exec");


module.exports={
    download:(params)=>{
        const {onProgress,onFail}=params;
        exec(onProgress,onFail,"Fetcher","download",[params]);
    },
    upload:(params)=>{
        const {onProgress,onFail}=params;
        exec(onProgress,onFail,"Fetcher","upload",[params]);
    },
};
