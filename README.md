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

- [x] Implement printPlan.md

- [x] Take out the emojis added to the "configuracion del sistema de impresion"

- [x] Get ride of "modo de impresion", fold any available printing configuration
to the template, like quality, margins, color, whatever the printer agnostic library
exposes

- [x] In any numeric input field, if it is currently "0", clear the field when opening it, so the
user can began typing directly.

- [x] Empty numeric input field is "0" not the previous value as if it was not changed.
e.g. focus a numeric input field, delete everything and save, it should be 0 not the previous value.

- [x] Ensure the user can exit all input fields by taping anywhere in the screen, 
any other input field or button, or dismissing the keyboard.

- [x] Any time the keyboard is opened for an input field, ensure to scroll the view
so the input field is in view above the keyboard.

- [x] there is a bug when scrolling, any time you tap->scroll(up/down)->lift 
the buttons of android that do back, main screen and apps opened?? the hamburger
that give you a list of apps opened currently, how do it is called?, well that 
set of buttons appear partially before disappearing, it do not happen when you 
tap an empty place without scrolling

- [x] Add a confirmation popup when selecting the number of copies to print.

- [x] Give a number integer as id for each template, show it in the template selection
section.

- [x] In  "gestion de cola de impresion" instead of showing (Lote #number) show
(Plantilla #ID)

- [x] In  "gestion de cola de impresion" there is a blank space between the total
pending, printring and printed counters and the "imprimir restantes" button, get
ride of that.

- [x] In  "gestion de cola de impresion>Items en cola" change the representation
of elements in the list from current to

`#Number-item - Plantilla Numero-plantilla - Página Numero-pagina`

Wrap the text if there is not enough space.

Below that put the file path.

To the side of both elements put a label indicating `Pendiente`, `Imprimiendo`, `Impreso`, `Fallido`

Maintain the current style to represent elements

- [x] Change "Items en Cola (N)" for "Cola de impresión (N)"

- [x] Implement the searching for printers through wifi capability.

- [ ] Implement/fix printing.

- [ ] In  "gestion de cola de impresion" allow me to tap into an item in the "items en cola"
list and choose to take it out, retry, print again, if it failed, show the error
here.

- [ ] In "gestion de cola de impresion", should there be a preview of the images??

- [ ] Add support for use of frontal camera.


## Made for use of

[Instagram Mimbi](https://www.instagram.com/mimbi_glitterbar)

WhatsApp: 343-5362802
