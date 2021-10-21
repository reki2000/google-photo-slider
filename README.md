# Google Photo Slider

This is a photo slide show app, showing photos and movies on the specified Google Photo's album. Especially this app is made to run on FireStick TV.

## Setup

Enable Google Photo API on your Google Cloud Platform account, and make a 'web application'
type OAuth credential for this app. You will get a `client_id` and `client_secret`.

Set them on `local.properties` as follows:
```
googlePhotoApiClientId=<YOUR CLIENT_ID>
googlePhotoApiClientSecret=<YOUR CLIENT_SECRET>
```

## Build

Open this project from Android Studio 2020.3.a

## Authentication

For the first launch of this app, Silk Browser will be shown. You can select your Google account to access your Google Photo contents.
After allowing this app to access Google Photo's contents, you will see the message like `...` on the browser screen.
Now you can close the browser and get back to this app. Slide show automatically starts.
