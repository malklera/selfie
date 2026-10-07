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

- [x] if there is no printer configured, do not open an android dialog to save as pdf when taping the printing button, just leave the picture in the queue as "Pendiente"

- [ ] Once i connect a printer and close the app, at opening check if the same connection is available and automatically connect if it is.

- [ ] a print job should only be send to print if a printer is available, otherwise keep it pending, if it is send to print and you do not get a positive response from the printer then it stays pending

- [ ] If the template for printing requires more than one picture, the state in
the printing queue when there is less pictures than required should say "Pendiente (Esperando más fotos)"
not "Pendiente (Esperando impresora disponible)" the latter text should only be
when there is not a printer connected

- [x] in the queue screen, make the whole screen scroll instead of only the queue

- [ ] Fix the style of "servicio kiosk" in printing config, use another word instead of "kiosk"

- [ ] Check how "imprimir restantes" works, if i want it to work that way when there is no printer.

- [ ] Implement/fix printing.

- [ ] There is a visual bug when searching for a wifi printer, while the spinner is show,
below "impresora wifi-manual" appear some empty rectangles, why is "conexion manua por ip" even there if
there is no input field for the ip?

- [ ] the input field for ip has to be numeric only, same for the port

- [ ] "buscar wifi" should open a new screen, not a floating window

- [ ] get ride of the "usar modo fake" for the printing output

- [ ] in "gestion de cola de impresion" align all the numbers acording to the largest text,
currently the "pendientes" get wrapped in a small screen(this is good) but the number
is not aligned with the rest

- [ ] in "gestion de cola de impresion"  the buttons that have icon+text, if the
screen is too small to properly wrap the text without cutting the words like
`imprimi
r`
do not show text, show only the icons

- [ ] when is a good moment to clean the queue?

- [ ] When pressing "Resetear a 0" in configuration screen, then entering to some other screen like "configuracion de impresora"
and going back, the counter goes back to before reseting, check all fields in the main
configuration screen to keep a temporary state of the changes if the user goes to another screen,
when coming back it should be the same unsaved state, only when exiting the configuration
screen by presing the "x" to close it and discarding the changes should the unsaved
changes be discarded

- [ ] In  "gestion de cola de impresion" allow me to tap into an item in the "items en cola"
list and choose to take it out, retry, print again, if it failed, show the error
here.

- [ ] In "gestion de cola de impresion", should there be a preview of the images??

- [ ] Add support for use of frontal camera.


## Made for use of

[Instagram Mimbi](https://www.instagram.com/mimbi_glitterbar)

WhatsApp: 343-5362802
