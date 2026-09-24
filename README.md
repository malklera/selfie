# Selfie

App to be used with the "Selfie Mirror", it is made to be used in a tablet.

## TODO

[x] Add a "Acerca de" section at the end of configuration with the build information:
"Version: release tag and commit, "Licensia": MIT, and "Autor": "github.com/malklera".
is there a way to automatically add the realease and commit when the app is build?

[x] Change the display of paths used in configuration to show something more human
readable instead of URIs.

[x] Add a toast when saving configurations

[x] Add a section in configuration that show the resolution of the screen of the
device.

[x] Add a login system for errors, add a section in configuration that take you
to a new screen where the history of all logins is keep, allow this logins to be
selected and copied.

Each time there is an error add a structured login with the following information:
timestamp, build commit, last action taken by the user, screen where the error occurs,
error message.

[ ] Think if I want to add a password for configuration.

[x] Add a section in configuration where you can choose the quality of the pictures

Use a dropdown menu, showing first the 9:16 resolutions supported by the hardware,
only show the supported ones, in descending order, after that show the 3:4 ones,
separate the sections and indicate the ratios.

Highlight the currently selected one.

When opening the dropdown move the scroll so that the selected resolution is in view.

When the dropdown is colapsed, show the selected resolution.

By default select max quality available 9:16.

The preview in the capture screen should be a scaled and cropped view of the camera,
if the aspect ratio of the hardware and screen are the same, just scale up/down as
needed, if they are different crop the preview to fit in the screen, ensure when saving
the image that the resolution is the same as in the preview, what the user see is
what is saved.

[x] Add a new section in configuration, below the main screen picture selection,
copy its way of showing miniature, path, informing of ratio difference with screen
resolution, file selection, all of it, this image will be used in the capture screen
to show in the preview and when saving the photo so make it available but do not use it yet

[x] Use the capture box image and show it on top of the preview of the camera
in the capture screen, resize to fit the screen even if it deforms, when the picture
is taken save the camera capture with the capture box on top as a single image,
what the user see is what is saved

[x] Replace loading logo for the initial splash screen that says "Selfie by Malklera"

[x] Scale down the view of the final image, where the "Toca para otra foto" 
buttom is, leave some margins below and above, enough for the buttom to not be
on top of the displayed image, center it, keep aspect ratio, do not change the
size of the saved image, only the display.

[x] Add "¡TU FOTO ESTÁ LISTA!" in bold, some print font, in yellow above the 
view of the image taken.

[x] Change the buttom text from the current one to "[emoji/icon of camera] OTRA FOTO"
With transparent fill, or the same black as the background, whatever is more
performing or consume less resources, leave a gray border, white text.

[x] Add some rounding to the corners of the image show, just apply to the showing, not
to the image. And double the padding above and below.

[x] Add a section to the configuration screen at the buttom, above the "Acerca de" section
called "Impresión"

There put a switch with the title "Mostrar botom", this will control if the "Imprimir"
buttom will be show in the screen where the "otra foto" is shown, do nothing about that
for now.

Below that put "Maximo número de impresiones" with a numeric input field besides

Below that, "Impresos" with the number of printings made, put a button besides
that will reset to 0 the counter.

Below that, "Selección de impresora", for now put nothing here, i want to learn
a little more about this.

Below that, "Modo de impresión", show a preview of the current mode selected,
if taped open a new screen where a list of modes will be show, a title of the mode
name, the preview of the mode, and some information about it, e.g. full width, single
image (height, width), quality(printing quality, not resolution), for now just put
a placeholder here.

[x] Move the config button show to the top left, same style and margins.

[ ] Add printing buttom to the screen where the "otra foto" button is show.

Only show if the switch in configuration>printing is on.

Show the same style as the "otra foto" button, "printer icon/emoji Imprimir" to
the right, same height, align both buttons to be equidistant, if there is not
enough space to show them, show only the icon/emoji of the buttons.



---

Impresión

Agregar sección a configuración
Switch to show botón
Contador de fotos impresas y máximo de impresiones, solo permitir ingresar números, agregar botón para resetear a 0 las fotos tomadas
Selección de impresora
Tipo de hoja
Seleccionnde plantillas de como imprimir las imágenes

En la sección que muestra la foto tomada mostrar botón de "emoji/icon impresora Imprimir" en la misma altura del botón otra foto, equidistante con respecto a los bordes


---

Tablet resolution: 1200*1904
Ratio: 75:119
