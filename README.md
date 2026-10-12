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

- [x] In "buscar impresoras wifi" in the list of found printers, get ride of the "Conectar" button
make the whole item clickable with the default behaviour to connect

- [x] In "buscar impresoras wifi" fix the IP input field, use the same style of labeling
as "Puerto" and "Nombre", and use "IPv4" as the label

- [x] Check all input fields, ensure you can exist it by taping any other button,
empty space or closing the keyboard, good behaviour "Cuenta regresiva", bad behaviour "IPv4"

- [x] In "buscar impresoras wifi" Ensure all input fields are completily in view
when the keyboard is opened

- [x] When there is no printer connected disable the "Desconectar" button with the
same strategy as the "Resetear a 0" button

- [x] When there is no printer selected, disable the button "Conectar/Verificar"
with the same strategy as "Desconectar"

- [ ] In "buscar impresoras wifi" "Conectar/Verificar" do not seems to do anything
once the printer is connected, do it actually check the connection? if so, show a popup
indicating the state

- [ ] Get ride of "Impresora Seleccionada" and the "Ninguna (Sin configurar)", when
there is a printer selected, show it in "Estado de la impresora" with its name like it is
currently show in "Impresora Seleccionada" and below/besides its state

- [ ] check connection to printer

- [ ] Check how "imprimir restantes" works, if i want it to work that way when there is no printer.

- [ ] Implement/fix printing.

- [ ] In  "gestion de cola de impresion" allow me to tap into an item in the "items en cola"
list and choose to take it out, retry, print again, if it failed, show the error
here.

- [ ] "Configuracion de impresora" do not show "Impresora seleccionada" and "Estado the la Impresora"
with a grey background, they seem like items in a list and they are not

- [ ] In "gestion de cola de impresion", should there be a preview of the images??

- [ ] Add support for use of frontal camera.

- [ ] Probably should update the color theme, think about this.

- [ ] Support for connecting a printer with bluethooth


## Made for use of

[Instagram Mimbi](https://www.instagram.com/mimbi_glitterbar)

WhatsApp: 343-5362802
