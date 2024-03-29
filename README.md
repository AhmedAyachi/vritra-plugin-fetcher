# <img width="20"  src="https://raw.githubusercontent.com/AhmedAyachi/RepoIllustrations/f7ee069a965d3558e0e7e2b7e6733d1a642c78c2/Vritra/Icon.svg"> ![GitHub license](https://img.shields.io/badge/vritra--plugin--fetcher-e03065) &middot; ![GitHub license](https://img.shields.io/badge/cordova--android-10.1.2-2eca55.svg) ![GitHub license](https://img.shields.io/badge/cordova--iOS-7-2eca55.svg) ![GitHub license](https://img.shields.io/badge/license-MIT-e03065.svg)

A cordova plugin for uploading/downloading files on android/iOS. 
Defines a global **Fetcher** object.

# Installation
After installing globally the cordova cli, execute:
```
cordova plugin add vritra-plugin-fetcher
```

>  \-\-force flag may be required if **vritra-plugin-webview** is present.

You may need to manually install pods on ios. In that case, in your project root directory, execute:
```
cd platforms/ios
pod install 
```
and you should be ready to fetch !

[See documentation](https://vritrajs.github.io/#cordovaplugins#fetcher)