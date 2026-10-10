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

- [ ] check connection to printer

- [ ] Once i connect a printer and close the app, at opening check if the same connection is available and automatically connect if it is.
Check with actual printer, it works with the android selection

- [ ] Check how "imprimir restantes" works, if i want it to work that way when there is no printer.

- [ ] Implement/fix printing.

- [ ] In  "gestion de cola de impresion" allow me to tap into an item in the "items en cola"
list and choose to take it out, retry, print again, if it failed, show the error
here.

- [ ] In "gestion de cola de impresion", should there be a preview of the images??

- [ ] Add support for use of frontal camera.

- [ ] Probably should update the color theme, think about this.

- [ ] Support for connecting a printer with bluethooth


## Made for use of

[Instagram Mimbi](https://www.instagram.com/mimbi_glitterbar)

WhatsApp: 343-5362802
