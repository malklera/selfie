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

- [ ] There is a time difference when the preview of the camera and the "recuadro" on top are show, there should not be.

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

- [x] In "Diagnostico de imagen" use n:n style to describe the aspect ratio in all three items.
Get ride of the grey background in this, it should be black/transparent as the title

- [x] Get ride of the estimated size of the image in "Tamaño de imagen"

- [x] In "Diagnostico de imagen" make each item below another, not besides each other

- [x] Make each item(version, licencia, autor each separated) in "acerca de" copy in tap.

- [x] The warning regarding the aspect ratio of "portada" and "cuadro de captura" are still there?,
i think the image i am testing with are not of the aspect ratio of the phone but i do not get the warning

- [ ] Test all type and combinations of aspect ratios

## Made for use of

[Instagram Mimbi](https://www.instagram.com/mimbi_glitterbar)

WhatsApp: 343-5362802
