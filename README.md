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

- [x] Move "Maximo numero de impresiones", "Impresos", and the whole section of "Configuracion del sistema de impresion" to a new printing screen, delete the "Mostrar boton" text and but the togle besides "Impresion", make "impresion" a button that take you to the printing screen 

- [ ] Check how "imprimir restantes" works, if i want it to work that way when there is no printer.

- [ ] In  "gestion de cola de impresion" allow me to tap into an item in the "items en cola"
list and choose to take it out, retry, print again, if it failed, show the error
here.

- [ ] "Configuracion de impresora" do not show "Impresora seleccionada" and "Estado the la Impresora"
with a grey background, they seem like items in a list and they are not

- [ ] In "gestion de cola de impresion", should there be a preview of the images??

- [ ] Add support for use of frontal camera.

- [ ] Probably should update the color theme, think about this.

- [ ] Support for connecting a printer with bluethooth

- [ ] Add toggle for accessing config screen with a pasword, numbers only, ensure there is a way of resetting it.

- [ ] Allow to change the number of taps to enter the config screen, minimal 1.


## Made for use of

[Instagram Mimbi](https://www.instagram.com/mimbi_glitterbar)

WhatsApp: 343-5362802
