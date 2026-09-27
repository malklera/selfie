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

- [ ] In "gestion de cola de impresion", the "(Lote #number)" what do it represent?
i expect that it show page printed when more than one image goes to one page, but
it do not seems like.

- [x] there is a bug when scrolling, any time you tap->scroll(up/down)->lift 
the buttons of android that do back, main screen and apps opened?? the hamburger
that give you a list of apps opened currently, how do it is called?, well that 
set of buttons appear partially before disappearing, it do not happen when you 
tap an empty place without scrolling

- [ ] Add a confirmation popup when selecting the number of copies to print.

- [ ] In "gestion de cola de impresion", should there be a preview of the images??

- [ ] Add support for use of frontal camera.


## Made for use of

[Instagram Mimbi](https://www.instagram.com/mimbi_glitterbar)

WhatsApp: 343-5362802
