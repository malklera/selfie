# Selfie

App to be used with the "Selfie Mirror", it is made to be used in a tablet.

Allows to take pictures with the available cameras and resolutions.

Select a main screen image to be show.

Select a border image to be added to the taken picture as a decoration, it do
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

- [ ] Allow to mark pictures to be printed, then inside config select all and print them with android print framework

- [ ] There is a difference when the preview of the camera and the "recuadro" on top, there should not be.

- [ ] Lock the app into portrait mode, or allow rotation, ask mom.

- [ ] Check quality of images taken, i think there is a problem

## Made for use of

[Instagram Mimbi](https://www.instagram.com/mimbi_glitterbar)

WhatsApp: 343-5362802
