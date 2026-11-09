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

- [x] There is a time difference when the preview of the camera and the "recuadro" on top are show, there should not be.

- [x] add a section to control rotatio of the app, portrait, landscape, auto, can this ignore whatever the device auto rotate toggle is at?

- [x] Put a button that says "Abrir" that will open the default file manager in the selected folder in "Destino", put it besides the "Seleccionar Carpeta" button

- [x] make each item in "Diagnostico de image" copy in tap, the same as in "acerca de"

- [x] Refactor the whole process of taking an image, preview and saving.

stretch+ajustar give me a 4:3(i think) image
stretch+rellenar give me bars above and below in the preview
stretch+4:3 give me a 4:3(i think) image, with black bars above and below in preview
stretch+16:9 give me a 16:9(i think) image, with black bars above and below in preview
the device is always use in portrait mode, so i think the aspect ratios have to be 9:16 instead, add a new option for this so i can see and compare

cover+ajustar in preview show a 4:3 picture with decoration cutoff above and below, with black bars above and below, in the captured image there is a black space that was not in the preview between the end of the picture at the right and the beginning of the decoration, that should not exist
cover+rellenar the decoration is cutoff to the sides, and the picture in the final image is cut off both above/below and on the sides
cover+4:3 the decoration is cut off above/below, the final image has a black space to the right that was not in the preview
cover+16:9 decoration is cut off above/below, picture is cutoff above/below and sides in the final image respect to the preview, there is a black space to the right that was not in the preview.

What is show in the preview is what is saved in the end, they cannot differ, the only difference is that for display it get scaled up/down to fit the screen, the final image is not scaled, fix all of this

- [x] "Relacion fija" change the display of options to a popup menu the same style as "tamaño de la imagen"

- [ ] Add support for use of frontal camera.

- [ ] Probably should update the color theme, think about this.

- [ ] Add toggle for accessing config screen with a pasword, numbers only, ensure there is a way of resetting it.

- [ ] Allow to change the number of taps to enter the config screen, minimum 1.


- [x] on tap of "configuracion de impresora" open a new screen that would allow you to search and connect to a printer, use whatever android/kotlin provides for this, if there is some api of framework, no need to implement from scrach

- [x] From printing branch copy the implementation for the templates. Each template is ONE future pdf page A4

- [ ] From printing branch, take inspiration for the queue and change the following:
Allow to select on/multiple/all elements of the queue, on select show options to take out of the queue, and printing
On print, generate a in memory pdf following the template then call upon the android printing service, (can you pass the printing connected information to the android side or do i have to select printing each time anyway?), what type of pre configured parametres can we pass to android so i do not have to select everything manually?
On selection of multiple elements generate a pdf with multiple pages, then the rest the same.
Only mark elements as printed if the android service returns some type of success state we can query, otherwise just use "Enviado" instead of "Pendiente", so at least
i know what i tried to print.

- [ ] Setting a config in the print screen, then saving in the main config screen resets whatever was in the print screen.

- [ ] Change the queue preview column to do not show the tick selector, allow to select one by maintaining the tapped on top of one item

- [x] Drop the use of # to indicate number

- [x] The queue screen make the whole part of the scroll, not only the list of items


## Made for use of

[Instagram Mimbi](https://www.instagram.com/mimbi_glitterbar)

WhatsApp: 343-5362802
