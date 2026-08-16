# Plan from human

Make active use of git to keep good history of your changes.

Ask the user for information not provided that may be needed to make the app.

Use kotlin for android.

Android minimum  **API 23** (Android 6.0) – maximum compatibility

Where possible use the standard library.

Where possible use the device provided APIs, input field, file picker, camera, etc.

The target device is a tablet.

---

The app is about taking pictures in parties.

Use the front camera.

Use camera-icon.svg as the app icon.

Use logo-1920_1920.png any time a loading screen is needed. Scale it as appropriate.

Use standard generic icons and fonts, whatever is builtin or more generic.

Use dark theme.

All user facing text has to be in spanish(my client is spanish speaking), the code will be in english.

Make it fullscreen.

Send a signal to the device to not sleep or automatically block the device.

All button are floating over whatever is being show.

---

# Main

The `main` screen when opening the app will be referred as "main".

May display any of the following:

- Black background with a message "Toca aqui para foto" in the center.

- png image

The default is set in the `configuration` screen, if not image is selected, the
message is show.

Show a button in the bottom right corner with a icon representing configuration, put 80%
transparency, once taped it will take to the `configuration` screen.

When tapping once in any part of the screen outside the button, change to the `capture` screen

---

# Configuration

Use standard file picker for everything, the default to show is the explore type
file picker with miniature view of files, only display the files that are valid
for the specific item, default place it opens is `internal storage/pictures`, remember
the folder of the last selected file and next time open it there, use per picker memory.

Put a title at the top `Configuración`

To the right put two buttons `Guardar` and `X`, `Guardar` will save the current
state of the config, `X` will close the `configuration` screen and go back to
the screen from where `configuration` was open, if there are unsaved state, show
a pop up message saying so with three buttons `Guardar`, `Descartar` and `Cancelar`,
`Guardar` save the state and close `configuration`, `Descartar` discard the current changes
and close `configuration`, and `Cancelar` close the pop up and stays in `configuration`

Make this two buttons floating at the top right, sticky so when scrolling down
they stay visible.

`Portada`: Display the given title, below show a preview of the current selected
item, below that show a file picker, this is what is show in `main`, it show be
possible to not have anything, to select no image.

`Cuenta regresiva`: Display the given title, below show the current count down, default
3 seconds, make it a numeric field input. This is the count down to use between
showing the `Capture` screen and taking the picture.

`Destino`: Display the given title, below display the full path to the folder where
the taken pictures are saved, make it a file picker, default `almacenamiento interno/Pictures/selfie`

---

# Capture

At entering the `capture` screen display the camera preview and on top a downward
counter at the center, 90% transparency. Ensure the countdown only began after the
preview is show.

Once the counter reach zero, take a picture.

Save the picture to the path selected in config, with the name being the timestamp,
`yyyy-mm-dd_hh-mm-ss.jpg`

Display the picture just taken.

After taking the picture display a button at the center bottom to take another picture,
which will take us to `main`. Only show the button after the picture is taken, when
displaying it.

Display the `configuration` button the same way as in `main` with the same functionality.

When entering->exiting the `configuration` screen, ensure it goes back to the displaying
part, not to the countdown.
