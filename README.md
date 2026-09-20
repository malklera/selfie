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
