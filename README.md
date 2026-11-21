# Selfie

App to be used with the "Selfie Mirror", it is made to be used in a tablet.

Allows to take pictures with the available cameras and resolutions.

Select a main screen image to show.

Select a border image to be added to the taken picture as a decoration, it does
not have to be a border, it can be any type of image with some part that is
transparent so the picture taken by the camera can be seen.

Printing support with several templates for the mode of printing.

## Building

```sh
JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleRelease
```

The .apk file is at

`app/build/outputs/apk/release/`

## TODO

- [ ] Add support for use of frontal camera.

- [ ] Probably should update the color theme, think about this.

- [ ] Add toggle for accessing config screen with a pasword, numbers only, ensure there is a way of resetting it.

- [ ] Allow to change the number of taps to enter the config screen, minimum 1.

- [ ] Refactor to allow the use of different languages, move all user facing text to use this system.

- [ ] Add support for other languages, began with english and portugues

- [ ] Add option to go back to welcome screen automatically, allow to set the time.

## Made for use of

[Instagram Mimbi](https://www.instagram.com/mimbi_glitterbar)

WhatsApp: 343-5362802
